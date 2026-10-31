package jp.xoki.web.zunda.smarthomewebserverbackend.batch.service;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.service.AgentMqttProvisioning;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchExecutionRequestDetailDto;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import com.fasterxml.jackson.databind.JsonNode;
import org.mockito.MockedConstruction;

class BatchCommandPublishServiceTest {
    private final AgentMqttProvisioning provisioning = mock(AgentMqttProvisioning.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final MqttClient client = mock(MqttClient.class);
    private final BatchCommandPublishService service = new BatchCommandPublishService(provisioning, mapper);
    private final UUID executionId = UUID.randomUUID();
    private final BatchExecutionRequestDetailDto request = BatchExecutionRequestDetailDto.builder()
            .executionId(executionId).executionCommand("private command must not be broadcast")
            .cancelRequestedAt(LocalDateTime.of(2027, 1, 1, 1, 0)).cancelGraceSeconds(30).build();

    @BeforeEach
    void configurePublisher() {
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "qos", 1);
        ReflectionTestUtils.setField(service, "connectionTimeout", 4);
        ReflectionTestUtils.setField(service, "keepAliveInterval", 60);
        ReflectionTestUtils.setField(service, "automaticReconnect", true);
        ReflectionTestUtils.setField(service, "brokerUrl", "tcp://127.0.0.1:18884");
        ReflectionTestUtils.setField(service, "clientId", "publisher-test");
        ReflectionTestUtils.setField(service, "username", "publisher-user");
        ReflectionTestUtils.setField(service, "password", "test-only-password");
        when(provisioning.isReady()).thenReturn(true);
        when(provisioning.executionTopic(executionId, "command")).thenReturn("test/command");
        when(provisioning.executionTopic(executionId, "cancel")).thenReturn("test/cancel");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void commandAndCancellationOnlyPublishTheirPublicProtocolFields(boolean cancellation) throws Exception {
        ReflectionTestUtils.setField(service, "client", client);
        when(client.isConnected()).thenReturn(true);
        assertThat(send(cancellation)).isTrue();
        ArgumentCaptor<MqttMessage> message = ArgumentCaptor.forClass(MqttMessage.class);
        verify(client).publish(eq(cancellation ? "test/cancel" : "test/command"), message.capture());
        assertThat(message.getValue().getQos()).isEqualTo(1);
        assertThat(message.getValue().isRetained()).isFalse();
        JsonNode payload = mapper.readTree(message.getValue().getPayload());
        assertThat(payload.get("execution_id").asText()).isEqualTo(executionId.toString());
        assertThat(payload.has("execution_command")).isFalse();
        assertThat(payload.size()).isEqualTo(cancellation ? 4 : 1);
        if (cancellation) {
            assertThat(payload.get("command").asText()).isEqualTo("cancel");
            assertThat(payload.get("cancel_requested_at").asText()).isEqualTo("2027-01-01T01:00:00");
            assertThat(payload.get("cancel_grace_seconds").asInt()).isEqualTo(30);
        }
        verify(client, never()).connect(any(MqttConnectOptions.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void disabledPublishingNeverResolvesTheAgentOrConnects(boolean cancellation) {
        ReflectionTestUtils.setField(service, "enabled", false);
        assertThat(service.isEnabled()).isFalse();
        assertThat(send(cancellation)).isFalse();
        verifyNoInteractions(provisioning, client);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unreadyAgentDoesNotOpenAMqttConnection(boolean cancellation) {
        when(provisioning.isReady()).thenReturn(false);
        assertThat(send(cancellation)).isFalse();
        verify(provisioning, never()).executionTopic(any(UUID.class), anyString());
        verifyNoInteractions(client);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingTopicDoesNotOpenAMqttConnection(boolean cancellation) {
        when(provisioning.executionTopic(executionId, cancellation ? "cancel" : "command")).thenReturn(null);
        assertThat(send(cancellation)).isFalse();
        verifyNoInteractions(client);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void brokerPublishFailuresAreNotReportedAsSuccessful(boolean cancellation) throws Exception {
        ReflectionTestUtils.setField(service, "client", client);
        when(client.isConnected()).thenReturn(true);
        MqttException failure = new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED);
        doThrow(failure).when(client).publish(anyString(), any(MqttMessage.class));
        assertThatThrownBy(() -> send(cancellation)).isInstanceOf(IllegalStateException.class).hasCause(failure);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void serializationFailureNeverContactsBroker(boolean cancellation) throws Exception {
        ObjectMapper brokenMapper = mock(ObjectMapper.class);
        JsonMappingException failure = JsonMappingException.fromUnexpectedIOE(new IOException("test serialization failure"));
        when(brokenMapper.writeValueAsBytes(any())).thenThrow(failure);
        BatchCommandPublishService publisher = new BatchCommandPublishService(provisioning, brokenMapper);
        ReflectionTestUtils.setField(publisher, "enabled", true);
        assertThatThrownBy(() -> send(publisher, cancellation))
                .isInstanceOf(IllegalStateException.class).hasCause(failure);
        verifyNoInteractions(client);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "test-credential"})
    void lazyClientCreationConnectsWithConfiguredOptionsAndAuthentication(String credential) throws Exception {
        ReflectionTestUtils.setField(service, "username", credential);
        ReflectionTestUtils.setField(service, "password", credential);
        try (MockedConstruction<MqttClient> construction = mockConstruction(MqttClient.class)) {
            assertThat(service.publish(request)).isTrue();
            assertThat(construction.constructed()).hasSize(1);
            MqttClient created = construction.constructed().getFirst();
            ArgumentCaptor<MqttConnectOptions> options = ArgumentCaptor.forClass(MqttConnectOptions.class);
            verify(created).connect(options.capture());
            verify(created).setTimeToWait(4000L);
            assertThat(options.getValue().isCleanSession()).isTrue();
            assertThat(options.getValue().getConnectionTimeout()).isEqualTo(4);
            assertThat(options.getValue().getKeepAliveInterval()).isEqualTo(60);
            assertThat(options.getValue().isAutomaticReconnect()).isTrue();
            boolean authenticated = credential != null && !credential.isBlank();
            assertThat(options.getValue().getUserName()).isEqualTo(authenticated ? credential : null);
            assertThat(options.getValue().getPassword()).isEqualTo(authenticated ? credential.toCharArray() : null);
        }
    }

    @Test
    void existingDisconnectedClientReconnectsAndShutdownOnlyDisconnectsAnActiveClient() throws Exception {
        service.disconnect();
        ReflectionTestUtils.setField(service, "client", client);
        when(client.isConnected()).thenReturn(false);
        service.disconnect();
        verify(client, never()).disconnect();
        assertThat(service.publish(request)).isTrue();
        verify(client).connect(any(MqttConnectOptions.class));
        when(client.isConnected()).thenReturn(true);
        service.disconnect();
        verify(client).disconnect();
    }

    private boolean send(boolean cancellation) {
        return send(service, cancellation);
    }

    private boolean send(BatchCommandPublishService publisher, boolean cancellation) {
        return cancellation ? publisher.publishCancellation(request) : publisher.publish(request);
    }
}
