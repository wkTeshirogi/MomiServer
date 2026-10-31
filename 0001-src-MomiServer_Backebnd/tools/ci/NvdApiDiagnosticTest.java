import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLHandshakeException;

/** Offline checks using only a loopback HTTP server and synthetic credentials. */
public final class NvdApiDiagnosticTest {

    /** Exercise response classification and secret handling without the real NVD.
     *
     * @param args unused
     * @throws Exception if any assertion or server operation fails
     */
    public static void main(String[] args) throws Exception {
        String secret = "synthetic-test-key-not-a-real-credential";
        AtomicInteger status = new AtomicInteger(200);
        AtomicReference<String> receivedKey = new AtomicReference<>();
        AtomicInteger redirectHits = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api", exchange -> {
            receivedKey.set(exchange.getRequestHeaders().getFirst("apiKey"));
            exchange.getResponseHeaders().set("message", secret);
            exchange.getResponseHeaders().set("Location", "/redirect-target");
            exchange.sendResponseHeaders(status.get(), 0);
            exchange.getResponseBody().write(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            exchange.close();
        });
        server.createContext("/redirect-target", exchange -> {
            redirectHits.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(250);
                exchange.sendResponseHeaders(200, -1);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IOException exception) {
                // The client is expected to disconnect after its deadline.
            } finally {
                exchange.close();
            }
        });
        server.start();
        try (HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()) {
            URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api");
            for (int code : new int[] {200, 403, 429, 500, 503, 302}) {
                status.set(code);
                Properties result = NvdApiDiagnostic.probe(client, endpoint, secret, Duration.ofSeconds(2));
                check("http_response".equals(result.getProperty("outcome")), "HTTP outcome");
                check(Integer.toString(code).equals(result.getProperty("httpStatus")), "HTTP status");
                check(secret.equals(receivedKey.get()), "Key header delivery");
                check(!serialize(result).contains(secret), "Secret must not be in diagnostic artifact");
                check(Long.parseLong(result.getProperty("elapsedMillis")) >= 0, "Elapsed time");
            }
            check(redirectHits.get() == 0, "Credential-bearing redirects must not be followed");
            for (String key : new String[] {null, "", "  "}) {
                Properties result = NvdApiDiagnostic.probe(client, endpoint, key, Duration.ofSeconds(2));
                check("false".equals(result.getProperty("keyPresent")), "Blank key flag");
                check(receivedKey.get() == null, "Blank key must not be sent");
            }
            Properties invalid = NvdApiDiagnostic.probe(client, endpoint, secret + "\r\n",
                    Duration.ofSeconds(2));
            check("invalid_request_configuration".equals(invalid.getProperty("outcome")), "Invalid header");
            check(!serialize(invalid).contains(secret), "Invalid header must not leak");
            Properties timedOut = NvdApiDiagnostic.probe(client, endpoint.resolve("/slow"), secret,
                    Duration.ofMillis(20));
            check("timeout".equals(timedOut.getProperty("outcome")), "Request deadline");
            check(!serialize(timedOut).contains(secret), "Timeout must not leak");
        } finally {
            server.stop(0);
        }
        check("dns_error".equals(NvdApiDiagnostic.classify(new IOException(new UnknownHostException(secret)))),
                "Nested DNS classification");
        check("tls_error".equals(NvdApiDiagnostic.classify(new SSLHandshakeException(secret))), "TLS classification");
        check("timeout".equals(NvdApiDiagnostic.classify(new HttpTimeoutException(secret))), "Timeout classification");
        check("connection_or_io_error".equals(NvdApiDiagnostic.classify(new IOException(secret))), "IO classification");
        System.out.println("NVD diagnostic: 16 offline scenarios passed.");
    }

    private static String serialize(Properties properties) throws IOException {
        StringWriter output = new StringWriter();
        properties.store(output, null);
        return output.toString();
    }

    private static void check(boolean condition, String name) {
        if (!condition) {
            throw new AssertionError(name);
        }
    }
}
