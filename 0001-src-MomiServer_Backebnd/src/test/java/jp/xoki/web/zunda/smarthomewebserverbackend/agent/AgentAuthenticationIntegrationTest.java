package jp.xoki.web.zunda.smarthomewebserverbackend.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.service.AgentSecrets;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchExecutionRequestCreateRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.service.BatchExecutionRequestService;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.service.BatchMaintenanceService;
import org.springframework.test.context.transaction.TestTransaction;
import org.junit.jupiter.api.Test;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.repository.AgentAuthRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.repository.AgentStatusRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.dto.AgentStatusDto.SnapshotRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.dto.AgentStatusDto.StorageSnapshot;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.model.AgentPrincipal;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.time.ApplicationClockFactory;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchExecutionRequestDetailDto;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.springframework.mock.web.MockHttpServletResponse;

/** 実PostgreSQLとSecurityFilterChainを使用して端末間境界・再送・失効を検証する。 */
@SpringBootTest(properties = {
        "mqtt.enabled=false", "notification.discord.enabled=false",
        "notification.discord.security-events.enabled=false", "spring.batch.job.enabled=false",
        "app.agent-auth.response-encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "app.batch.maintenance-marker=${java.io.tmpdir}/momi-agent-maintenance-test-${random.uuid}.flag"
})
@AutoConfigureMockMvc
@Transactional
class AgentAuthenticationIntegrationTest {
    private static final int HTTP_OK = 200;
    private static final int HTTP_UNAUTHORIZED = 401;
    private static final int HTTP_FORBIDDEN = 403;
    private static final int HTTP_NOT_FOUND = 404;
    private static final int HTTP_CONFLICT = 409;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final int RATE_LIMIT = 60;
    private static final int READY_TIMEOUT_SECONDS = 10;
    private static final int RESULT_TIMEOUT_SECONDS = 15;
    private static final int MQTT_TIMEOUT_SECONDS = 5;
    private static final int MQTT_TIMEOUT_MILLIS = 5000;
    private static final int MQTT_SUBSCRIPTION_DENIED = 128;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired BatchExecutionRequestService executionService;
    @Autowired BatchMaintenanceService maintenance;
    @Autowired AgentAuthRepository authRepository;
    @Autowired AgentStatusRepository statusRepository;
    @Autowired jp.xoki.web.zunda.smarthomewebserverbackend.agent.service.AgentMqttProvisioning mqtt;
    @Autowired jp.xoki.web.zunda.smarthomewebserverbackend.agent.config.AgentMqttProperties mqttProperties;
    @Autowired jp.xoki.web.zunda.smarthomewebserverbackend.agent.service.MosquittoControl mqttControl;

    @Test
    void maintenanceReturnsUnstartedStateWithoutCommittingAStart() throws Exception {
        long owner = device();
        JsonNode tokens = register(owner, KeyPairGenerator.getInstance("Ed25519").generateKeyPair());
        UUID id = execution(owner, 1L);
        maintenance.setPaused(true);
        try {
            JsonNode deferred = call(post("/api/v1/agent/batch/executions/" + id + "/start")
                    .header("Authorization", bearer(tokens)), HTTP_OK);
            assertThat(deferred.path("execution_id").asText()).isEqualTo(id.toString());
            assertThat(deferred.path("execution_status").asText()).isEqualTo("queued");
            assertThat(jdbc.queryForObject("select started_at is null from t_batch_execution_requests where execution_id=?",
                    Boolean.class, id)).isTrue();
        } finally {
            // テスト用の外側トランザクションもゲートを保持するため、ロールバック後に明示再開する。
            TestTransaction.flagForRollback();
            TestTransaction.end();
            maintenance.setPaused(false);
        }
    }

    @Test
    void runtimeEndpointsAreScopedAndPersistHeartbeatOutputAndUnknownState() throws Exception {
        long owner = device();
        JsonNode tokens = register(owner, KeyPairGenerator.getInstance("Ed25519").generateKeyPair());
        JsonNode other = register(device(), KeyPairGenerator.getInstance("Ed25519").generateKeyPair());
        UUID id = execution(owner, 1L);
        String path = "/api/v1/agent/batch/executions/" + id;
        JsonNode sync = call(get("/api/v1/agent/runtime/sync").header("Authorization", bearer(tokens)), HTTP_OK);
        assertThat(sync.path("server_time").asText()).isNotBlank();
        assertThat(sync.get("execution_ids").toString()).contains(id.toString());
        assertThat(call(get("/api/v1/agent/runtime/sync").header("Authorization", bearer(other)), HTTP_OK)
                .get("execution_ids").toString()).doesNotContain(id.toString());
        call(get(path + "/command").header("Authorization", bearer(other)), HTTP_NOT_FOUND);
        assertThat(call(get(path + "/command").header("Authorization", bearer(tokens)), HTTP_OK)
                .path("server_time").asText()).isNotBlank();
        assertThat(call(json("/api/v1/agent/heartbeat", Map.of("mqtt_connected", true))
                .header("Authorization", bearer(tokens)), HTTP_OK).path("server_time").asText()).isNotBlank();
        assertThat(jdbc.queryForObject("select heartbeat_at is not null from m_agents where device_id=?",
                Boolean.class, owner)).isTrue();
        call(json(path + "/unconfirmed", Map.of("reason", "restart"))
                .header("Authorization", bearer(other)), HTTP_NOT_FOUND);
        call(json(path + "/unconfirmed", Map.of("reason", "restart")).header("Authorization", bearer(tokens)), HTTP_OK);
        assertThat(jdbc.queryForObject(
                "select confirmation_status from t_batch_execution_requests where execution_id=?",
                String.class, id)).isEqualTo("start_unconfirmed");
        call(post(path + "/start").header("Authorization", bearer(tokens)), HTTP_OK);
        call(json(path + "/complete", Map.of("execution_status", "success", "exit_code", 0,
                "stdout", "hello", "stderr", "warning", "output_truncated", false))
                .header("Authorization",bearer(tokens)),HTTP_OK);
        assertThat(jdbc.queryForObject("select stdout from t_batch_execution_requests where execution_id=?",
                String.class, id)).isEqualTo("hello");
        call(json(path + "/complete", Map.of("execution_status","success","stdout","changed"))
                .header("Authorization",bearer(tokens)),HTTP_OK);
        assertThat(jdbc.queryForObject("select stdout from t_batch_execution_requests where execution_id=?",
                String.class, id)).isEqualTo("hello");
    }

    @Test
    void olderSnapshotDoesNotReplaceChildrenAndEqualTimestampCanRefresh() throws Exception {
        long device = device();
        JsonNode tokens = register(device, KeyPairGenerator.getInstance("Ed25519").generateKeyPair());
        UUID agent = UUID.fromString(tokens.path("agentId").asText());
        AgentPrincipal principal = new AgentPrincipal(agent, device, 1L);
        // DBのマイクロ秒精度へ揃え、同時刻の比較自体を検査する。
        OffsetDateTime time = OffsetDateTime.now(ApplicationClockFactory.systemClock())
                .withNano(0).minusSeconds(5);
        statusRepository.save(principal, new SnapshotRequest(time, 10.25, 20.75, null, null, 100,
                List.of(new StorageSnapshot("root", "Current", 1000, 200)), List.of()));
        statusRepository.save(principal, new SnapshotRequest(time.minusSeconds(1), 50, 60, 99.0, 99.0, 50,
                List.of(new StorageSnapshot("root", "Stale", 1000, 900)), List.of()));
        assertThat(jdbc.queryForObject("select cpu_percent from t_agent_machine_status where agent_id=?", Double.class, agent))
                .isEqualTo(10.25);
        assertThat(jdbc.queryForObject("select cpu_temperature_c from t_agent_machine_status where agent_id=?", Double.class, agent))
                .isNull();
        assertThat(jdbc.queryForObject("select display_name from t_agent_storage_status where agent_id=?", String.class, agent))
                .isEqualTo("Current");
        statusRepository.save(principal, new SnapshotRequest(time, 15, 25, 0.0, null, 110,
                List.of(new StorageSnapshot("root", "Equal time", 1000, 250)), List.of()));
        assertThat(jdbc.queryForObject("select cpu_temperature_c from t_agent_machine_status where agent_id=?", Double.class, agent))
                .isZero();
        assertThat(jdbc.queryForObject("select display_name from t_agent_storage_status where agent_id=?", String.class, agent))
                .isEqualTo("Equal time");
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentAuthenticationCountersRemainAtomic() throws Exception {
        String bucket = "jooq-test-" + UUID.randomUUID();
        long minute = TimeUnit.MILLISECONDS.toMinutes(ApplicationClockFactory.systemClock().millis());
        try (ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(4)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> attempts = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                attempts.add(executor.submit(() -> {
                    start.await();
                    return authRepository.countRequest(bucket, minute);
                }));
            }
            start.countDown();
            List<Integer> results = new ArrayList<>();
            for (Future<Integer> attempt : attempts) results.add(attempt.get(20, TimeUnit.SECONDS));
            assertThat(results).containsExactlyInAnyOrder(1, 2, 3, 4);
            assertThat(jdbc.queryForObject("select attempts from t_agent_auth_rate_limits where bucket_key=? and window_minute=?",
                    Integer.class, bucket, minute)).isEqualTo(4);
        } finally {
            jdbc.update("delete from t_agent_auth_rate_limits where bucket_key=?", bucket);
        }
    }

    @Test
    void currentGenerationSnapshotReplacesFutureDatedPreviousGeneration() throws Exception {
        long device = device();
        KeyPair firstKey = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        JsonNode first = register(device, firstKey);
        UUID agentId = UUID.fromString(first.get("agentId").asText());
        jdbc.update("""
                insert into t_agent_machine_status(
                    agent_id, generation, collected_at, cpu_percent, memory_percent, uptime_seconds)
                values (?, 1, now() + interval '4 minutes', 10, 20, 100)
                """, agentId);

        change(device, "pending");
        JsonNode replacement = register(device, KeyPairGenerator.getInstance("Ed25519").generateKeyPair());
        call(json("/api/v1/agent/status/snapshot", Map.of(
                "collected_at", OffsetDateTime.now(ApplicationClockFactory.systemClock()).toString(),
                "cpu_percent", 30,
                "memory_percent", 40,
                "uptime_seconds", 200,
                "storage", List.of(),
                "services", List.of()))
                .header("Authorization", bearer(replacement)), HTTP_OK);

        assertThat(jdbc.queryForObject(
                "select generation from t_agent_machine_status where agent_id = ?", Long.class, agentId))
                .isEqualTo(2L);
        assertThat(jdbc.queryForObject(
                "select cpu_percent from t_agent_machine_status where agent_id = ?", Integer.class, agentId))
                .isEqualTo(30);
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="AGENT_MQTT_TEST",matches="true")
    void mosquittoCredentialsBindIdentityTopicsAndDisconnectOnRevocation() throws Exception {
        mqttProperties.setEnabled(true);
        mqttProperties.setBrokerUrl("tcp://127.0.0.1:18884");
        mqttProperties.setAgentBrokerUrl("tcp://127.0.0.1:18884");
        mqttProperties.setUsername("runtime-admin"); mqttProperties.setPassword("runtime-test-only");
        mqttProperties.setAllowInsecureLocalhost(true);
        long owner = device();
        JsonNode tokens = register(owner, KeyPairGenerator.getInstance("Ed25519").generateKeyPair());
        String clientId = tokens.get("mqtt").get("clientId").asText();
        MqttClient client = new MqttClient("tcp://127.0.0.1:18884", clientId, new MemoryPersistence());
        client.setTimeToWait(MQTT_TIMEOUT_MILLIS);
        try {
            mqtt.reconcile();
            JsonNode config = call(get("/api/v1/agent/mqtt/settings").header("Authorization",bearer(tokens)),HTTP_OK);
            assertThat(config.get("enabled").asBoolean()).isTrue();
            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName(config.get("username").asText());
            options.setPassword(config.get("password").asText().toCharArray());
            options.setConnectionTimeout(MQTT_TIMEOUT_SECONDS);
            client.connect(options);
            IMqttToken allowed=client.subscribeWithResponse(config.get("commandTopic").asText(),1);
            assertThat(allowed.getGrantedQos()[0]).isEqualTo(1);
            IMqttToken denied=client.subscribeWithResponse("smarthome/agents/another/batch/command",1);
            assertThat(denied.getGrantedQos()[0]).isEqualTo(MQTT_SUBSCRIPTION_DENIED);
            try (MqttClient impersonator = new MqttClient("tcp://127.0.0.1:18884", "wrong-" + UUID.randomUUID(),
                    new MemoryPersistence())) {
                assertThrows(MqttException.class, () -> impersonator.connect(options));
            }
            UUID execution=execution(owner,1L);
            assertThat(mqtt.executionTopic(execution,"command")).isEqualTo(config.get("commandTopic").asText());
            change(owner,"disabled");
            assertThat(mqtt.executionTopic(execution,"command")).isNull();
            mqtt.reconcile();
            org.awaitility.Awaitility.await()
                    .atMost(java.time.Duration.ofSeconds(MQTT_TIMEOUT_SECONDS))
                    .until(() -> !client.isConnected());
            assertThat(client.isConnected()).isFalse();
            assertThrows(MqttException.class, () -> client.connect(options));
        } finally {
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
            try {
                mqttControl.command("disableClient", Map.of("username", clientId));
            } finally {
                mqttProperties.setEnabled(false);
            }
        }
    }

    private long device() {
        return jdbc.queryForObject("insert into m_devices(device_name,address,status) values "
                + "(?,'127.0.0.1','1') returning id",
                Long.class, "auth-test-" + UUID.randomUUID());
    }

    private JsonNode call(MockHttpServletRequestBuilder builder, int expected) throws Exception {
        MockHttpServletResponse response = mvc.perform(builder).andExpect(status().is(expected)).andReturn().getResponse();
        return response.getContentAsString().isEmpty()
                 ? mapper.createObjectNode() : mapper.readTree(response.getContentAsString());
    }

    private MockHttpServletRequestBuilder json(String path, Object body) throws Exception {
        return post(path).contentType("application/json").content(mapper.writeValueAsBytes(body));
    }

    private String code(long device) throws Exception {
        return call(post("/api/master/device/" + device + "/agent/registration-code")
                .with(user("admin").roles("ADMIN")), HTTP_OK).get("registrationCode").asText();
    }

    private String proof(KeyPair key, String purpose, UUID id, String credential) throws Exception {
        String nonce = call(post("/api/v1/agent/auth/challenge"), HTTP_OK).get("nonce").asText();
        return Jwts.builder().audience().add("momiserver-agent-" + purpose).and()
                .claim("requestId", id.toString()).claim("credentialHash", AgentSecrets.hash(credential))
                .claim("nonce", nonce).signWith(key.getPrivate(), Jwts.SIG.EdDSA).compact();
    }

    private Map<String, Object> registration(String code, UUID requestId, KeyPair key) throws Exception {
        return Map.of("registrationCode", code, "registrationRequestId", requestId,
                "publicKey", Base64.getEncoder().encodeToString(key.getPublic().getEncoded()),
                "proof", proof(key, "register", requestId, code), "hostname", "fixture",
                "os", "linux", "agentVersion", "0.1.0");
    }

    private JsonNode register(long device, KeyPair key) throws Exception {
        return call(json("/api/v1/agent/register", registration(code(device), UUID.randomUUID(), key)), HTTP_OK);
    }

    private String bearer(JsonNode tokens) {
        return "Bearer " + tokens.get("accessToken").asText();
    }

    private JsonNode refresh(String token, UUID id, KeyPair key, int status) throws Exception {
        return call(json("/api/v1/agent/auth/refresh", Map.of("refreshToken", token, "refreshRequestId", id,
                "proof", proof(key, "refresh", id, token))), status);
    }

    @Test
    void adminOnlyCodeAndRegistrationRecoveryNeverRevealCredentialsToAdmin() throws Exception {
        long device = device();
        call(post("/api/master/device/" + device + "/agent/registration-code"), HTTP_UNAUTHORIZED);
        call(post("/api/master/device/" + device + "/agent/registration-code")
                .with(user("reader").roles("USER")), HTTP_FORBIDDEN);
        KeyPair key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String code = code(device);
        UUID requestId = UUID.randomUUID();
        Map<String, Object> body = registration(code, requestId, key);
        JsonNode tokens = call(json("/api/v1/agent/register", body), HTTP_OK);
        assertThat(tokens.get("mqtt").get("enabled").asBoolean()).isFalse();
        call(json("/api/v1/agent/register", body), HTTP_UNAUTHORIZED); // nonce cannot be replayed
        JsonNode recovered = call(json("/api/v1/agent/register", registration(code, requestId, key)), HTTP_OK);
        assertThat(recovered).isEqualTo(tokens);
        call(json("/api/v1/agent/register", registration(code, UUID.randomUUID(), key)), HTTP_UNAUTHORIZED);
        call(json("/api/v1/agent/register", registration(code, requestId,
                KeyPairGenerator.getInstance("Ed25519").generateKeyPair())), HTTP_UNAUTHORIZED);
        assertThat(jdbc.queryForObject("select count(*) from t_agent_sessions where agent_id = ?", Integer.class,
                UUID.fromString(tokens.get("agentId").asText()))).isEqualTo(1);
        JsonNode admin = call(get("/api/master/device/" + device + "/agent")
                .with(user("admin").roles("ADMIN")), HTTP_OK);
        assertThat(admin.has("accessToken")).isFalse();
        assertThat(admin.has("refreshToken")).isFalse();
        assertThat(admin.has("registrationCode")).isFalse();
        call(post("/api/master/device/" + device + "/agent/registration-code")
                .with(user("admin").roles("ADMIN")), HTTP_CONFLICT);
    }

    @Test
    void reissuedAndExpiredCodesAreRejectedWithoutConsumingRegistration() throws Exception {
        long device = device();
        String old = code(device);
        String current = code(device);
        KeyPair key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        call(json("/api/v1/agent/register", registration(old, UUID.randomUUID(), key)), HTTP_UNAUTHORIZED);
        jdbc.update("update m_agents set code_expires_at = now() - interval '1 second' where device_id = ?", device);
        call(json("/api/v1/agent/register", registration(current, UUID.randomUUID(), key)), HTTP_UNAUTHORIZED);
        assertThat(jdbc.queryForObject("select status from m_agents where device_id = ?", String.class, device))
                .isEqualTo("pending");
        register(device, key);
    }

    @Test
    void accessIsAgentOnlyAndRefreshIsKeyBoundRotatingAndRecoverable() throws Exception {
        KeyPair key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        JsonNode initial = register(device(), key);
        call(get("/api/v1/agent/me").header("Authorization", bearer(initial)), HTTP_OK);
        call(get("/api/v1/agent/batch/settings").header("Authorization", bearer(initial)), HTTP_OK);
        call(get("/api/notifications").header("Authorization", bearer(initial)), HTTP_UNAUTHORIZED);
        call(get("/api/v1/agent/me").with(user("admin").roles("ADMIN")), HTTP_FORBIDDEN);
        call(get("/api/v2/agent/me").with(user("admin").roles("ADMIN")), HTTP_NOT_FOUND);
        call(get("/api/v2/agent/me").header("Authorization", bearer(initial)), HTTP_NOT_FOUND);
        call(get("/api/v1/agent/batch/settings").with(user("misconfigured-user").roles("AGENT")), HTTP_FORBIDDEN);
        call(get("/api/v1/agent/me").header("X-API-Key", "anything"), HTTP_UNAUTHORIZED);
        call(get("/api/v1/agent/me").header("X-API-Key", "anything")
                .header("Authorization", bearer(initial)), HTTP_OK);
        call(get("/api/v1/agent/download/" + UUID.randomUUID())
                .header("Authorization", bearer(initial)), HTTP_NOT_FOUND);
        String old = initial.get("refreshToken").asText();
        UUID requestId = UUID.randomUUID();
        refresh(old, requestId, KeyPairGenerator.getInstance("Ed25519").generateKeyPair(), HTTP_UNAUTHORIZED);
        JsonNode next = refresh(old, requestId, key, HTTP_OK);
        call(get("/api/v1/agent/me").header("Authorization", bearer(initial)), HTTP_UNAUTHORIZED);
        call(get("/api/v1/agent/me").header("Authorization", bearer(next)), HTTP_OK);
        assertThat(refresh(old, requestId, key, HTTP_OK)).isEqualTo(next);
        refresh(old, UUID.randomUUID(), key, HTTP_UNAUTHORIZED);
        jdbc.update("update t_agent_sessions set recovery_expires_at = now() - "
                + "interval '1 second' where access_hash = ?",
                AgentSecrets.hash(next.get("accessToken").asText()));
        refresh(old, requestId, key, HTTP_UNAUTHORIZED);
        jdbc.update("update t_agent_sessions set refresh_expires_at = now() - "
                + "interval '1 second' where access_hash = ?",
                AgentSecrets.hash(next.get("accessToken").asText()));
        refresh(next.get("refreshToken").asText(), UUID.randomUUID(), key, HTTP_UNAUTHORIZED);
    }

    @Test
    void apiVersionStateControlsAgentAccessWithoutChangingAgentAuthentication() throws Exception {
        JsonNode tokens = register(device(), KeyPairGenerator.getInstance("Ed25519").generateKeyPair());
        jdbc.update("update m_api_versions set status='deprecated', "
                + "deprecated_dt=timestamp '2026-01-01 00:00:00' "
                + "where api_group='agent' and version='v1'");
        MockHttpServletResponse deprecated = mvc.perform(get("/api/v1/agent/me")
                .header("Authorization", bearer(tokens))).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(deprecated.getHeader("Deprecation")).startsWith("@");
        call(get("/api/v1/agent/me").header("X-API-Key", "anything"), HTTP_UNAUTHORIZED);

        jdbc.update("update m_api_versions set status='retired' "
                + "where api_group='agent' and version='v1'");
        call(get("/api/v1/agent/me").header("Authorization", bearer(tokens)), 410);
    }

    private UUID execution(long device, Long generation) {
        long definition = jdbc.queryForObject("""
                insert into m_batch_definitions(batch_name,view_name,category,execution_type,log_path,execution_command)
                values (?,'fixture','test','shell','test.log','echo test') returning id
                """, Long.class, UUID.randomUUID().toString());
        return jdbc.queryForObject("""
                insert into t_batch_execution_requests(batch_definition_id,target_agent_id,target_agent_generation,
                requested_type,requested_by,execution_status,execution_command,start_deadline_at,
                result_wait_timeout_seconds,cancel_grace_seconds,confirmation_status)
                values (?, ?, ?, 'manual','fixture','queued','echo test',now() + interval '1 hour',3600,30,'pending')
                returning execution_id
                """, UUID.class, definition, device, generation);
    }

    @Test
    void manualRequestSnapshotsTheRegisteredGeneration() throws Exception {
        long device = device();
        register(device, KeyPairGenerator.getInstance("Ed25519").generateKeyPair());
        UUID fixture = execution(device, null);
        long definitionId = jdbc.queryForObject(
                "select batch_definition_id from t_batch_execution_requests where execution_id=?", Long.class, fixture);
        BatchExecutionRequestCreateRequest request = new BatchExecutionRequestCreateRequest();
        request.setTargetAgentId(device);
        jdbc.update("""
                insert into m_batch_definition_parameters
                (batch_definition_id,parameter_name,parameter_type,fixed_value,display_order)
                values (?,'sample','fixed','original',1)
                """, definitionId);
        BatchExecutionRequestDetailDto created = executionService.createManualRequest(definitionId, request, "fixture");
        assertThat(created.getParameters()).containsEntry("sample","original");
        jdbc.update("update m_batch_definition_parameters set fixed_value='changed' where batch_definition_id=?",
                definitionId);
        assertThat(executionService.getDetail(created.getExecutionId()).getParameters())
                .containsEntry("sample", "original");
        assertThat(jdbc.queryForObject(
                "select target_agent_generation from t_batch_execution_requests where execution_id=?",
                Long.class, created.getExecutionId())).isEqualTo(1L);
        change(device, "pending");
        BatchExecutionRequestDetailDto unassigned = executionService.createManualRequest(definitionId, request, "fixture");
        assertThat(jdbc.queryForObject(
                "select target_agent_generation from t_batch_execution_requests where execution_id=?",
                Long.class, unassigned.getExecutionId())).isNull();
    }

    @Test
    void executionAndFilesRequireBothDeviceAndRegistrationGeneration() throws Exception {
        long owner = device();
        long other = device();
        KeyPair ownerKey = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        JsonNode tokens = register(owner, ownerKey);
        JsonNode otherTokens = register(other, KeyPairGenerator.getInstance("Ed25519").generateKeyPair());
        UUID execution = execution(owner, 1L);
        String path = "/api/v1/agent/batch/executions/" + execution;
        call(get(path + "/state").header("Authorization", bearer(tokens)), HTTP_OK);
        call(get(path + "/files").header("Authorization", bearer(tokens)), HTTP_OK);
        for (String suffix : new String[]{"/state", "/files", "/files/1/download"}) {
            call(get(path + suffix).header("Authorization", bearer(otherTokens)), HTTP_NOT_FOUND);
        }
        call(post(path + "/start").header("Authorization", bearer(otherTokens)), HTTP_NOT_FOUND);
        call(json(path + "/complete", Map.of("execution_status", "success", "exit_code", 0))
                .header("Authorization", bearer(otherTokens)), HTTP_NOT_FOUND);
        assertThat(jdbc.queryForObject("select execution_status from t_batch_execution_requests where execution_id = ?",
                String.class, execution)).isEqualTo("queued");
        call(post(path + "/start").header("Authorization", bearer(tokens)), HTTP_OK);
        call(json(path + "/complete", Map.of("execution_status", "success", "exit_code", 0))
                .header("Authorization", bearer(tokens)), HTTP_OK);
        UUID legacy = execution(owner, null);
        call(get("/api/v1/agent/batch/executions/" + legacy + "/state")
                .header("Authorization", bearer(tokens)), HTTP_NOT_FOUND);
        change(owner, "pending");
        call(get("/api/v1/agent/me").header("Authorization", bearer(tokens)), HTTP_UNAUTHORIZED);
        refresh(tokens.get("refreshToken").asText(), UUID.randomUUID(), ownerKey, HTTP_UNAUTHORIZED);
        JsonNode replacement = register(owner, KeyPairGenerator.getInstance("Ed25519").generateKeyPair());
        assertThat(replacement.get("generation").asLong()).isEqualTo(2);
        assertThat(replacement.get("agentId")).isEqualTo(tokens.get("agentId"));
        call(get(path + "/state").header("Authorization", bearer(replacement)), HTTP_NOT_FOUND);
        UUID current = execution(owner, 2L);
        call(get("/api/v1/agent/batch/executions/" + current + "/state")
                .header("Authorization", bearer(replacement)), HTTP_OK);
    }

    private void change(long device, String status) throws Exception {
        call(json("/api/master/device/" + device + "/agent/status", Map.of("status", status, "reason", "test"))
                .with(user("admin").roles("ADMIN")), HTTP_OK);
    }

    @Test
    void agentDisableAndDeviceDisableBlockExistingTokens() throws Exception {
        long device = device();
        KeyPair key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        JsonNode tokens = register(device, key);
        jdbc.update("update m_devices set status = '0' where id = ?", device);
        call(get("/api/v1/agent/me").header("Authorization", bearer(tokens)), HTTP_UNAUTHORIZED);
        refresh(tokens.get("refreshToken").asText(), UUID.randomUUID(), key, HTTP_UNAUTHORIZED);
        jdbc.update("update m_devices set status = '1' where id = ?", device);
        change(device, "disabled");
        call(get("/api/v1/agent/me").header("Authorization", bearer(tokens)), HTTP_UNAUTHORIZED);
        refresh(tokens.get("refreshToken").asText(), UUID.randomUUID(), key, HTTP_UNAUTHORIZED);
    }

    @Test
    void bootstrapRequestsAreRateLimitedAndDoNotTrustForwardedFor() throws Exception {
        for (int i = 0; i < RATE_LIMIT; i++) {
            call(post("/api/v1/agent/auth/challenge").with(r -> {
                    r.setRemoteAddr("192.0.2.99");
                    return r;
                })
                    .header("X-Forwarded-For", "198.51.100." + i), HTTP_OK);
        }
        call(post("/api/v1/agent/auth/challenge").with(r -> {
                    r.setRemoteAddr("192.0.2.99");
                    return r;
                })
                .header("X-Forwarded-For", "203.0.113.1"), HTTP_TOO_MANY_REQUESTS);
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void competingRegistrationsConsumeCodeOnlyOnce() throws Exception {
        long device = device();
        String code = code(device);
        Map<String, Object> first = registration(code, UUID.randomUUID(), KeyPairGenerator.getInstance("Ed25519").generateKeyPair());
        Map<String, Object> second = registration(code, UUID.randomUUID(), KeyPairGenerator.getInstance("Ed25519").generateKeyPair());
        CountDownLatch ready = new java.util.concurrent.CountDownLatch(2);
        CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        try (ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            ArrayList<Future<Integer>> futures = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for (Map<String, Object> body : java.util.List.of(first, second)) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(READY_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)) {
                        throw new AssertionError("start timeout");
                    }
                    return mvc.perform(json("/api/v1/agent/register", body)).andReturn().getResponse().getStatus();
                }));
            }
            assertThat(ready.await(READY_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            ArrayList<Integer> results = new java.util.ArrayList<Integer>();
            for (Future<Integer> future : futures) {
                results.add(future.get(RESULT_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS));
            }
            assertThat(results).containsExactlyInAnyOrder(HTTP_OK, HTTP_UNAUTHORIZED);
        }
        assertThat(jdbc.queryForObject("""
                select count(*) from t_agent_sessions s inner join m_agents a on a.id=s.agent_id where a.device_id=?
                """, Integer.class, device)).isEqualTo(1);
        change(device, "disabled");
    }

    @Test
    void expiredAccessAndRegistrationRecoveryAreRejected() throws Exception {
        long device = device();
        String code = code(device);
        KeyPair key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        UUID request = UUID.randomUUID();
        JsonNode tokens = call(json("/api/v1/agent/register", registration(code, request, key)), HTTP_OK);
        jdbc.update("update t_agent_sessions set access_expires_at=now()-interval '1 second' where access_hash=?",
                AgentSecrets.hash(tokens.get("accessToken").asText()));
        call(get("/api/v1/agent/me").header("Authorization", bearer(tokens)), HTTP_UNAUTHORIZED);
        jdbc.update("update m_agents set recovery_expires_at=now()-interval '1 second' where device_id=?", device);
        call(json("/api/v1/agent/register", registration(code, request, key)), HTTP_CONFLICT);
        refresh(tokens.get("refreshToken").asText(), UUID.randomUUID(), key, HTTP_OK);
    }
}
