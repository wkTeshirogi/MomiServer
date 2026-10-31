package jp.xoki.web.zunda.smarthomewebserverbackend.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.GlobalExceptionHandler;
import jp.xoki.web.zunda.smarthomewebserverbackend.status.dto.StatusDto.Channel;
import jp.xoki.web.zunda.smarthomewebserverbackend.status.service.StatusWebSocketTicketService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.apache.tomcat.util.http.fileupload.impl.FileCountLimitExceededException;
import org.mockito.ArgumentCaptor;
import org.postgresql.util.ByteStreamWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.sql.Connection;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/** 実ソケットと専用検証DBでコンテナ・JDBCの更新互換性を検証する。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "mqtt.enabled=false", "app.agent-mqtt.enabled=false", "notification.discord.enabled=false",
        "notification.discord.security-events.enabled=false", "spring.batch.job.enabled=false",
        "app.scheduling.enabled=false", "app.vehicle-inspection-notification.enabled=false",
        "gateway.enabled=false", "gateway.openapi.public-enabled=true"
})
@Import(TomcatJdbcSecurityIntegrationTest.MultipartProbe.class)
class TomcatJdbcSecurityIntegrationTest {

    @LocalServerPort private int port;
    @Autowired private StatusWebSocketTicketService tickets;
    @Autowired private ObjectMapper mapper;
    @Autowired private DataSource dataSource;
    @MockitoSpyBean private GlobalExceptionHandler errors;

    @Test
    void realHttpKeepsPublicAndProtectedEndpointsSeparate() throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            assertThat(get(client, "/swagger-ui/index.html").statusCode()).isEqualTo(200);
            assertThat(get(client, "/api/notifications").statusCode()).isEqualTo(401);
            assertThat(get(client, "/api/v1/external/inventory/stocks").statusCode()).isEqualTo(401);
            assertThat(get(client, "/swagger-ui/index.html").statusCode()).isEqualTo(200);
        }
    }

    @Test
    void realMultipartParserAcceptsOrdinaryUploadAndRejectsExcessiveParts() throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            assertThat(upload(client, 2).body()).isEqualTo("2");
            assertThat(upload(client, 51).statusCode()).isEqualTo(500);
            ArgumentCaptor<Exception> failure = ArgumentCaptor.forClass(Exception.class);
            verify(errors).handleUnexpected(failure.capture(), any(HttpServletRequest.class));
            assertThat(failure.getValue()).hasRootCauseInstanceOf(FileCountLimitExceededException.class);
            assertThat(upload(client, 2).body()).isEqualTo("2");
        }
    }

    @ParameterizedTest
    @EnumSource(Channel.class)
    void realWebSocketDeliversSnapshotAndRejectsTicketReplay(Channel channel) throws Exception {
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated("transport-test", null,
                AuthorityUtils.createAuthorityList("ROLE_OPERATOR"));
        String ticket = tickets.issue(channel, authentication).ticket();
        URI uri = URI.create("ws://127.0.0.1:" + port + channel.path() + "?ticket=" + ticket);
        CompletableFuture<String> snapshot = new CompletableFuture<String>();
        CompletableFuture<Integer> closed = new CompletableFuture<Integer>();
        try (HttpClient client = HttpClient.newHttpClient()) {
            WebSocket socket = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(uri, new WebSocket.Listener() {
                        private final StringBuilder text = new StringBuilder();

                        @Override
                        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                            text.append(data);
                            if (last) {
                                snapshot.complete(text.toString());
                            }
                            ws.request(1);
                            return null;
                        }

                        @Override
                        public CompletionStage<?> onClose(WebSocket ws, int status, String reason) {
                            closed.complete(status);
                            return null;
                        }

                        @Override
                        public void onError(WebSocket ws, Throwable error) {
                            snapshot.completeExceptionally(error);
                            closed.completeExceptionally(error);
                        }
                    }).get(10, TimeUnit.SECONDS);
            try {
                assertThat(mapper.readTree(snapshot.get(10, TimeUnit.SECONDS)).path("type").asText())
                        .isEqualTo(channel == Channel.MACHINES ? "machine_update" : "service_update");
                assertThatThrownBy(() -> client.newWebSocketBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .buildAsync(uri, new WebSocket.Listener() { }).get(10, TimeUnit.SECONDS))
                        .hasCauseInstanceOf(WebSocketHandshakeException.class);
                socket.sendText("not-allowed", true).get(10, TimeUnit.SECONDS);
                assertThat(closed.get(10, TimeUnit.SECONDS)).isEqualTo(1008);
            } finally {
                socket.abort();
            }
        }
    }

    @Test
    void shortJdbcWriterPadsWithZerosInsteadOfEarlierStatementBytes() throws Exception {
        assertJdbcWriterRoundTrip(300000, 200000);
    }

    @Test
    void ordinaryJdbcWriterPreservesPayloadAcrossBufferBoundaries() throws Exception {
        assertJdbcWriterRoundTrip(200000, 200000);
    }

    private void assertJdbcWriterRoundTrip(int declared, int actual) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("CREATE TEMP TABLE jdbc_padding_probe (payload bytea) ON COMMIT DROP");
                }
                try (PreparedStatement marker = connection.prepareStatement("SELECT length(?)")) {
                    marker.setString(1, "synthetic-jdbc-sentinel;".repeat(350));
                    try (ResultSet result = marker.executeQuery()) {
                        assertThat(result.next()).isTrue();
                    }
                }
                byte[] payload = new byte[actual];
                Arrays.fill(payload, (byte) 'A');
                try (PreparedStatement insert = connection.prepareStatement("INSERT INTO jdbc_padding_probe VALUES (?)")) {
                    insert.setObject(1, new ByteStreamWriter() {
                        @Override
                        public int getLength() {
                            return declared;
                        }

                        @Override
                        public void writeTo(ByteStreamTarget target) throws IOException {
                            target.getOutputStream().write(payload);
                        }
                    });
                    assertThat(insert.executeUpdate()).isEqualTo(1);
                }
                try (Statement statement = connection.createStatement();
                        ResultSet result = statement.executeQuery("SELECT payload FROM jdbc_padding_probe")) {
                    assertThat(result.next()).isTrue();
                    byte[] stored = result.getBytes(1);
                    assertThat(stored).hasSize(declared);
                    assertThat(Arrays.copyOfRange(stored, 0, actual)).isEqualTo(payload);
                    assertThat(Arrays.mismatch(Arrays.copyOfRange(stored, actual, declared), new byte[declared - actual]))
                            .as("First non-zero padding byte (CVE-2026-107315)").isEqualTo(-1);
                }
            } finally {
                connection.rollback();
            }
        }
    }

    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> upload(HttpClient client, int parts) throws Exception {
        String part = """
                --probe
                Content-Disposition: form-data; name="file"; filename="test.txt"
                Content-Type: text/plain

                payload
                """.replace("\n", "\r\n");
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/swagger-ui/multipart-probe"))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "multipart/form-data; boundary=probe")
                .POST(HttpRequest.BodyPublishers.ofString(part.repeat(parts) + "--probe--\r\n")).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @TestComponent
    @RestController
    static class MultipartProbe {
        @PostMapping("/swagger-ui/multipart-probe")
        String countParts(HttpServletRequest request) throws Exception {
            return Integer.toString(request.getParts().size());
        }
    }
}
