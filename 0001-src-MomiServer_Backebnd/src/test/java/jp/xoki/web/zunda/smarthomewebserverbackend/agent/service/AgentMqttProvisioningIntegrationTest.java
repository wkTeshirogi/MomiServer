package jp.xoki.web.zunda.smarthomewebserverbackend.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.config.AgentMqttProperties;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.model.AgentPrincipal;
import org.jooq.DSLContext;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.repository.AgentMqttProvisioningRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@SpringBootTest(properties = {"mqtt.enabled=false", "app.agent-mqtt.enabled=false",
        "notification.discord.enabled=false", "notification.discord.security-events.enabled=false",
        "spring.batch.job.enabled=false", "app.vehicle-inspection-notification.enabled=false"})
@Transactional
class AgentMqttProvisioningIntegrationTest {
    @Autowired DSLContext dsl;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    private final AgentSecrets secrets = mock(AgentSecrets.class);
    private final MosquittoControl control = mock(MosquittoControl.class);
    private final AgentMqttProperties properties = new AgentMqttProperties();
    private final ObjectMapper mapper = new ObjectMapper();
    private final UUID agentId = UUID.randomUUID();
    private final String clientId = "coverage-" + agentId;
    private final List<Map.Entry<String, Map<String, Object>>> commands = new ArrayList<>();
    private AgentMqttProvisioning provisioning;
    private long deviceId;
    private boolean existingClient;
    private boolean existingRole;

    @BeforeEach
    void prepareBrokerSimulator() {
        properties.setEnabled(true);
        properties.setAgentBrokerUrl("tcp://127.0.0.1:18884");
        properties.setAllowInsecureLocalhost(true);
        provisioning = new AgentMqttProvisioning(new AgentMqttProvisioningRepository(dsl), secrets, properties, control, transactions);
        when(secrets.seal(anyString(), anyString())).thenReturn("sealed-test-password");
        when(secrets.open("sealed-test-password", "mqtt:" + clientId, String.class)).thenReturn("test-password");
        when(control.command(anyString(), anyMap())).thenAnswer(invocation -> {
            String command = invocation.getArgument(0);
            Map<String, Object> payload = invocation.getArgument(1);
            commands.add(Map.entry(command, payload));
            ObjectNode response = mapper.createObjectNode();
            if ("listClients".equals(command)) {
                ArrayNode clients = response.putArray("clients").add("unrelated-client");
                if (existingClient) {
                    clients.add(clientId);
                }
            }
            if ("listRoles".equals(command)) {
                ArrayNode roles = response.putArray("roles").add("unrelated-role");
                if (existingRole) {
                    roles.add(clientId);
                }
            }
            return response;
        });
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void reconciliationAcknowledgesScopedAclAndCredentialsBeforeBecomingReady(
            boolean clientExists, boolean roleExists) {
        existingClient = clientExists;
        existingRole = roleExists;
        agent(null);
        assertThat(provisioning.status(agentId, 1)).isEqualTo("pending");
        assertThat(provisioning.connection(new AgentPrincipal(agentId, deviceId, 1)).enabled()).isFalse();
        provisioning.reconcile();
        assertThat(syncStatus(1)).isEqualTo("ready");
        assertThat(commands).extracting(Map.Entry::getKey).containsSubsequence("listClients", "listRoles",
                roleExists ? "modifyRole" : "createRole",
                clientExists ? "modifyClient" : "createClient", "enableClient");
        JsonNode role = mapper.valueToTree(payload(roleExists ? "modifyRole" : "createRole"));
        assertThat(role.get("rolename").asText()).isEqualTo(clientId);
        assertThat(role.get("acls").size()).isEqualTo(13);
        for (JsonNode acl : role.get("acls")) {
            if (acl.get("allow").asBoolean()) {
                assertThat(acl.get("topic").asText()).isIn(AgentMqttProvisioning.topic(clientId, "command"),
                        AgentMqttProvisioning.topic(clientId, "cancel"));
                assertThat(acl.get("priority").asInt()).isPositive();
            } else {
                assertThat(acl.get("topic").asText()).isIn("#", "$CONTROL/#", "$SYS/#");
            }
        }
        Map<String, Object> clientPayload = payload(clientExists ? "modifyClient" : "createClient");
        assertThat(clientPayload).containsEntry("username", clientId).containsEntry("clientid", clientId)
                .containsEntry("password", "test-password");
        AgentMqttProvisioning.Connection connection = provisioning.connection(new AgentPrincipal(agentId, deviceId, 1));
        assertThat(connection.enabled()).isTrue();
        assertThat(connection.password()).isEqualTo("test-password");
        int commandCount = commands.size();
        provisioning.reconcile();
        assertThat(commands).hasSize(commandCount);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void disabledAgentRevokesAccessWhetherOrNotTheBrokerClientStillExists(boolean clientExists) {
        existingClient = clientExists;
        agent("ready");
        jdbc.update("update m_agents set status='disabled' where id=?", agentId);
        assertThat(provisioning.status(agentId, 1)).isEqualTo("revocation_pending");
        provisioning.reconcile();
        assertThat(syncStatus(1)).isEqualTo("revoked");
        assertThat(commands.stream().anyMatch(command -> "disableClient".equals(command.getKey())))
                .isEqualTo(clientExists);
        assertThat(commands).extracting(Map.Entry::getKey).doesNotContain("enableClient", "createRole", "createClient");
        assertThat(provisioning.connection(new AgentPrincipal(agentId, deviceId, 1)).enabled()).isFalse();
    }

    @Test
    void newGenerationWaitsUntilOldGenerationHasBeenRevoked() {
        existingClient = true;
        agent("ready");
        jdbc.update("update m_agents set generation=2,mqtt_client_id=? where id=?", clientId + "-2", agentId);
        doThrow(new IllegalStateException("test revoke failure")).when(control).command(eq("disableClient"), anyMap());
        provisioning.reconcile();
        assertThat(syncStatus(1)).isEqualTo("ready");
        assertThat(syncStatus(2)).isEqualTo("pending");
        assertThat(commands).extracting(Map.Entry::getKey).doesNotContain("enableClient");
        doReturn(mapper.createObjectNode()).when(control).command(eq("disableClient"), anyMap());
        when(secrets.open("sealed-test-password", "mqtt:" + clientId + "-2", String.class)).thenReturn("test-password");
        provisioning.reconcile();
        assertThat(syncStatus(1)).isEqualTo("revoked");
        assertThat(syncStatus(2)).isEqualTo("ready");
    }

    @Test
    void failedBrokerCommandLeavesCredentialsPendingForRetryWithTheSameSecret() {
        agent(null);
        doThrow(new IllegalStateException("test broker failure")).when(control).command(anyString(), anyMap());
        provisioning.reconcile();
        assertThat(syncStatus(1)).isEqualTo("pending");
        assertThat(jdbc.queryForObject("select encrypted_password from t_agent_mqtt_credentials where agent_id=?",
                String.class, agentId)).isEqualTo("sealed-test-password");
        assertThat(provisioning.status(agentId, 1)).isEqualTo("pending");
    }

    @Test
    void disabledConfigurationNeverMutatesBrokerState() {
        properties.setEnabled(false);
        assertThat(provisioning.isReady()).isFalse();
        assertThat(provisioning.status(agentId, 1)).isEqualTo("not_configured");
        assertThat(provisioning.executionTopic(UUID.randomUUID(), "command")).isNull();
        provisioning.reconcile();
        verifyNoInteractions(control, secrets);
    }

    @Test
    void executionTopicsRequireTheCurrentAgentGenerationAndReadyCredentials() {
        agent("ready");
        Long definition = jdbc.queryForObject("""
                insert into m_batch_definitions(batch_name,view_name,category,execution_type,log_path)
                values (?,'Topic test','test','shell','test.log') returning id
                """, Long.class, "mqtt-topic-" + UUID.randomUUID());
        UUID execution = jdbc.queryForObject("""
                insert into t_batch_execution_requests(batch_definition_id,target_agent_id,target_agent_generation,
                requested_type,execution_status,execution_command,start_deadline_at)
                values (?,?,1,'manual','success','echo test',CURRENT_TIMESTAMP) returning execution_id
                """, UUID.class, definition, deviceId);
        assertThat(provisioning.executionTopic(execution, "command"))
                .isEqualTo(AgentMqttProvisioning.topic(clientId, "command"));
        assertThat(provisioning.executionTopic(UUID.randomUUID(), "cancel")).isNull();
        jdbc.update("update m_agents set generation=2 where id=?", agentId);
        assertThat(provisioning.executionTopic(execution, "command")).isNull();
    }

    private void agent(String credentialStatus) {
        deviceId = jdbc.queryForObject("insert into m_devices(device_name,address,status) "
                + "values ('MQTT fixture','192.0.2.20','1') returning id", Long.class);
        jdbc.update("insert into m_agents(id,device_id,generation,status,mqtt_client_id) values (?,?,1,'registered',?)",
                agentId, deviceId, clientId);
        if (credentialStatus != null) {
            jdbc.update("insert into t_agent_mqtt_credentials"
                    + "(agent_id,generation,client_id,encrypted_password,sync_status) "
                    + "values (?,1,?,'sealed-test-password',?)", agentId, clientId, credentialStatus);
        }
    }

    private String syncStatus(long generation) {
        return jdbc.queryForObject("select sync_status from t_agent_mqtt_credentials where agent_id=? and generation=?",
                String.class, agentId, generation);
    }

    private Map<String, Object> payload(String operation) {
        return commands.stream().filter(command -> operation.equals(command.getKey()))
                .findFirst().orElseThrow().getValue();
    }
}
