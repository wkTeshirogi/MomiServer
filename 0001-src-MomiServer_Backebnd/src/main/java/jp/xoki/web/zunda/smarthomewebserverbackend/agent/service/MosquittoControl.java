package jp.xoki.web.zunda.smarthomewebserverbackend.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.config.AgentMqttProperties;
import lombok.RequiredArgsConstructor;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.stereotype.Component;

import static jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants.MosquittoControlConstants.TIMEOUT_SECONDS;
import static jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants.MosquittoControlConstants.TOPIC;

/** Serial, correlated Dynamic Security requests. Secret payloads must never be logged. */
@Component
@RequiredArgsConstructor
public class MosquittoControl {
    private final AgentMqttProperties properties;
    private final ObjectMapper mapper;
    private final Map<String, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private MqttClient client;

    public static boolean validBroker(String value, boolean local) {
        try {
            URI uri = URI.create(value);
            return uri.getHost() != null && uri.getUserInfo() == null && uri.getQuery() == null
                    && uri.getFragment() == null && (uri.getPath() == null || uri.getPath().isEmpty())
                    && ("ssl".equals(uri.getScheme()) || (local && "tcp".equals(uri.getScheme())
                    && Set.of("127.0.0.1", "localhost", "[::1]", "::1").contains(uri.getHost())));
        } catch (RuntimeException _) {
            return false;
        }
    }

    public synchronized JsonNode command(String operation, Map<String, ?> values) {
        String id = UUID.randomUUID().toString();
        CompletableFuture<JsonNode> reply = new CompletableFuture<JsonNode>();
        pending.put(id, reply);
        try {
            connect();
            LinkedHashMap<String, Object> command = new LinkedHashMap<String, Object>(values);
            command.put("command", operation);
            command.put("correlationData", id);
            client.publish(TOPIC, mapper.writeValueAsBytes(Map.of("commands", List.of(command))), 1, false);
            JsonNode result = reply.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (result.has("error")) {
                throw new IllegalStateException("MQTT control command rejected: " + operation);
            }
            return result.path("data");
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("MQTT control synchronization interrupted: " + operation);
        } catch (Exception _) {
            throw new IllegalStateException("MQTT control synchronization failed: " + operation);
        } finally {
            pending.remove(id);
        }
    }

    private void connect() throws MqttException {
        if (!validBroker(properties.getBrokerUrl(), properties.isAllowInsecureLocalhost())
                || properties.getUsername().isBlank() || properties.getPassword().isBlank()) {
            throw new IllegalStateException("Agent MQTT control configuration required");
        }
        if (client == null) {
            client = new MqttClient(properties.getBrokerUrl(), properties.getClientId(), new MemoryPersistence());
            client.setTimeToWait(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
        }
        if (client.isConnected()) {
            return;
        }
        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(properties.getUsername());
        options.setPassword(properties.getPassword().toCharArray());
        options.setConnectionTimeout(TIMEOUT_SECONDS);
        options.setHttpsHostnameVerificationEnabled(true);
        options.setCleanSession(true);
        client.connect(options);
        client.subscribe(TOPIC + "/response", 1, (topic, message) -> {
            try {
                for (JsonNode response : mapper.readTree(message.getPayload()).path("responses")) {
                    CompletableFuture<JsonNode> future = pending.get(response.path("correlationData").asText());
                    if (future != null) {
                        future.complete(response);
                    }
                }
            } catch (Exception _) { /* malformed reply times out without revealing secrets */ }
        });
    }

    @PreDestroy
    public synchronized void close() throws MqttException {
        if (client != null) {
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
        }
    }
}
