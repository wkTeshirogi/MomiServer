package jp.xoki.web.zunda.smarthomewebserverbackend.agent.service;

import static jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants.AgentMqttProvisioningConstants.ACLTYPE_VALUE;
import static jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants.AgentMqttProvisioningConstants.ALLOW_VALUE;
import static jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants.AgentMqttProvisioningConstants.PRIORITY_VALUE;
import static jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants.AgentMqttProvisioningConstants.TOPIC_VALUE;

import jp.xoki.web.zunda.smarthomewebserverbackend.agent.config.AgentMqttProperties;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.model.AgentPrincipal;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.model.AgentMqttStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.repository.AgentMqttProvisioningRepository;
import org.jooq.Record;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static jp.xoki.web.zunda.smarthomewebserverbackend.agent.model.AgentRegistrationStatus.REGISTERED;

import static jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants.AgentServiceConstants.ALLOW_PRIORITY;
import static jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants.AgentServiceConstants.FIELD_AGENT_ID;
import static jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants.AgentServiceConstants.FIELD_GENERATION;
import static jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants.AgentServiceConstants.FIELD_STATUS;
import static jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants.AgentServiceConstants.FIELD_USERNAME;
import static jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants.AgentServiceConstants.MQTT_SECRET_CONTEXT;
import com.fasterxml.jackson.databind.JsonNode;

/** Durable desired state is reconciled with Mosquitto; acknowledgements precede ready/revoked. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentMqttProvisioning {
    private final AgentMqttProvisioningRepository repository;
    private final AgentSecrets secrets;
    private final AgentMqttProperties properties;
    private final MosquittoControl control;
    private final PlatformTransactionManager transactions;

    public boolean isReady() {
        return properties.isEnabled() && MosquittoControl.validBroker(properties.getAgentBrokerUrl(),
                properties.isAllowInsecureLocalhost());
    }

    public record Connection(boolean enabled, String status, String brokerUrl, String clientId,
            String username, String password, String commandTopic, String cancelTopic) { }

    public String status(UUID agentId, long generation) {
        if (!isReady()) {
            return AgentMqttStatus.NOT_CONFIGURED.getStoredName();
        }
        String value = repository.status(agentId, generation);
        return value == null ? AgentMqttStatus.PENDING.getStoredName() : value;
    }

    public Connection connection(AgentPrincipal principal) {
        Record row = repository.connection(principal);
        if (!isReady() || row == null) {
            return new Connection(false, status(principal.agentId(), principal.generation()),
                    null, null, null, null, null, null);
        }
        String id = row.get("client_id", String.class);
        return new Connection(true, AgentMqttStatus.READY.getStoredName(), properties.getAgentBrokerUrl(), id, id,
                secrets.open(row.get("encrypted_password", String.class), MQTT_SECRET_CONTEXT + id, String.class),
                topic(id, "command"), topic(id, "cancel"));
    }

    public static String topic(String clientId, String kind) {
        return "smarthome/agents/" + clientId + "/batch/" + kind;
    }

    public String executionTopic(UUID executionId, String kind) {
        if (!isReady()) {
            return null;
        }
        String clientId = repository.executionClientId(executionId);
        return clientId == null ? null : topic(clientId, kind);
    }

    /** MQTT資格情報とブローカー側権限の同期。
     *
     * 定期実行・明示呼び出しで共用し、未設定時の停止と同期失敗時の再試行をここで管理する。
     */
    public void reconcile() {
        if (!isReady()) {
            return;
        }
        try {
            // Commit credentials before any broker mutation, so retries always reuse the same secret.
            for (Record agent : repository.registeredAgents()) {
                String id = agent.get("mqtt_client_id", String.class);
                repository.create(agent.get("id", UUID.class), agent.get(FIELD_GENERATION, Long.class), id,
                        secrets.seal(AgentSecrets.randomToken(""), MQTT_SECRET_CONTEXT + id));
            }
            for (Record row : repository.pending()) {
                new TransactionTemplate(transactions).executeWithoutResult(tx -> synchronize(row));
            }
        } catch (RuntimeException _) {
            // No command payloads/credentials in logs. Pending rows remain durable for the next pass.
            log.warn("Agent MQTT synchronization pending; check broker control configuration/connectivity");
        }
    }

    private void synchronize(Record key) {
        Record device = repository.lockDevice(key.get("device_id", Long.class));
        Record agent = repository.lockAgent(key.get(FIELD_AGENT_ID, UUID.class));
        Record row = repository.lockCredentials(key.get(FIELD_AGENT_ID, UUID.class), key.get(FIELD_GENERATION, Long.class));
        if (device == null || agent == null || row == null) {
            return; // A deleted reconciliation target must not grant broker access.
        }
        String id = row.get("client_id", String.class);
        boolean active = REGISTERED.getStoredName().equals(agent.get(FIELD_STATUS))
                && !"0".equals(device.get(FIELD_STATUS))
                && agent.get(FIELD_GENERATION, Long.class).equals(row.get(FIELD_GENERATION, Long.class));
        boolean exists = contains(control.command("listClients", Map.of("verbose", false, "count", -1)), "clients", id);
        if (!active) {
            if (exists) {
                control.command("disableClient", Map.of(FIELD_USERNAME, id));
            }
            repository.markStatus(id, AgentMqttStatus.REVOKED.getStoredName());
            return;
        }
        Integer old = repository.countUnrevokedOtherGenerations(row.get(FIELD_AGENT_ID, UUID.class), row.get(FIELD_GENERATION, Long.class));
        if (old == null || old > 0 || AgentMqttStatus.READY.getStoredName().equals(row.get("sync_status"))) {
            return;
        }
        ArrayList<Map<String, Object>> acls = new ArrayList<Map<String, Object>>();
        for (String type : List.of("publishClientSend", "publishClientReceive", "subscribePattern")) {
            for (String denied : List.of("#", "$CONTROL/#", "$SYS/#")) {
                acls.add(Map.of(ACLTYPE_VALUE, type, TOPIC_VALUE, denied, ALLOW_VALUE, false, PRIORITY_VALUE, 0));
            }
        }
        for (String kind : List.of("command", "cancel")) {
            acls.add(Map.of(ACLTYPE_VALUE, "subscribeLiteral", TOPIC_VALUE, topic(id, kind),
                    ALLOW_VALUE, true, PRIORITY_VALUE, ALLOW_PRIORITY));
            acls.add(Map.of(ACLTYPE_VALUE, "publishClientReceive", TOPIC_VALUE, topic(id, kind),
                    ALLOW_VALUE, true, PRIORITY_VALUE, ALLOW_PRIORITY));
        }
        boolean roleExists = contains(control.command("listRoles", Map.of("verbose", false, "count", -1)), "roles", id);
        control.command(roleExists ? "modifyRole" : "createRole", Map.of("rolename", id, "acls", acls));
        control.command(exists ? "modifyClient" : "createClient", Map.of(FIELD_USERNAME, id, "clientid", id,
                "password", secrets.open(row.get("encrypted_password", String.class), MQTT_SECRET_CONTEXT + id, String.class),
                "roles", List.of(Map.of("rolename", id, PRIORITY_VALUE, 1)), "groups", List.of()));
        control.command("enableClient", Map.of(FIELD_USERNAME, id));
        repository.markStatus(id, AgentMqttStatus.READY.getStoredName());
    }

    private boolean contains(com.fasterxml.jackson.databind.JsonNode data, String field, String name) {
        for (JsonNode item : data.path(field)) {
            if (name.equals(item.asText())) {
                return true;
            }
        }
        return false;
    }
}
