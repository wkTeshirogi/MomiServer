package jp.xoki.web.zunda.smarthomewebserverbackend.agent.repository;

import static jp.xoki.web.zunda.jooq.Tables.M_AGENTS;
import static jp.xoki.web.zunda.smarthomewebserverbackend.agent.model.AgentRegistrationStatus.REGISTERED;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.model.AgentMqttStatus;
import static jp.xoki.web.zunda.jooq.Tables.M_DEVICES;
import static jp.xoki.web.zunda.jooq.Tables.T_AGENT_MQTT_CREDENTIALS;
import static jp.xoki.web.zunda.jooq.Tables.T_BATCH_EXECUTION_REQUESTS;
import static org.jooq.impl.DSL.currentOffsetDateTime;
import static org.jooq.impl.DSL.when;

import java.util.List;
import java.util.UUID;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.model.AgentPrincipal;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

/** ブローカー同期の永続化を担当する。ロックの取得順序は端末、Agent、資格情報とする。 */
@Repository
@RequiredArgsConstructor
public class AgentMqttProvisioningRepository {
    private final DSLContext dsl;

    public String status(UUID agentId, long generation) {
        return dsl.select(when(T_AGENT_MQTT_CREDENTIALS.SYNC_STATUS.ne(AgentMqttStatus.REVOKED.getStoredName())
                        .and(M_AGENTS.STATUS.ne(REGISTERED.getStoredName()).or(M_DEVICES.STATUS.eq("0"))), AgentMqttStatus.REVOCATION_PENDING.getStoredName())
                        .otherwise(T_AGENT_MQTT_CREDENTIALS.SYNC_STATUS))
                .from(T_AGENT_MQTT_CREDENTIALS).innerJoin(M_AGENTS).on(M_AGENTS.ID.eq(T_AGENT_MQTT_CREDENTIALS.AGENT_ID))
                .innerJoin(M_DEVICES).on(M_DEVICES.ID.eq(M_AGENTS.DEVICE_ID))
                .where(T_AGENT_MQTT_CREDENTIALS.AGENT_ID.eq(agentId))
                .and(T_AGENT_MQTT_CREDENTIALS.GENERATION.eq(generation)).fetchOne(0, String.class);
    }

    public Record connection(AgentPrincipal principal) {
        return dsl.select(T_AGENT_MQTT_CREDENTIALS.fields()).from(T_AGENT_MQTT_CREDENTIALS)
                .innerJoin(M_AGENTS).on(M_AGENTS.ID.eq(T_AGENT_MQTT_CREDENTIALS.AGENT_ID))
                .innerJoin(M_DEVICES).on(M_DEVICES.ID.eq(M_AGENTS.DEVICE_ID))
                .where(M_AGENTS.ID.eq(principal.agentId())).and(M_AGENTS.GENERATION.eq(principal.generation()))
                .and(T_AGENT_MQTT_CREDENTIALS.GENERATION.eq(M_AGENTS.GENERATION))
                .and(M_AGENTS.STATUS.eq(REGISTERED.getStoredName())).and(M_DEVICES.STATUS.ne("0"))
                .and(T_AGENT_MQTT_CREDENTIALS.SYNC_STATUS.eq(AgentMqttStatus.READY.getStoredName())).fetchOne();
    }

    public String executionClientId(UUID executionId) {
        return dsl.select(T_AGENT_MQTT_CREDENTIALS.CLIENT_ID).from(T_BATCH_EXECUTION_REQUESTS)
                .innerJoin(M_AGENTS).on(M_AGENTS.DEVICE_ID.eq(T_BATCH_EXECUTION_REQUESTS.TARGET_AGENT_ID)
                        .and(M_AGENTS.GENERATION.eq(T_BATCH_EXECUTION_REQUESTS.TARGET_AGENT_GENERATION)))
                .innerJoin(M_DEVICES).on(M_DEVICES.ID.eq(M_AGENTS.DEVICE_ID))
                .innerJoin(T_AGENT_MQTT_CREDENTIALS).on(T_AGENT_MQTT_CREDENTIALS.AGENT_ID.eq(M_AGENTS.ID)
                        .and(T_AGENT_MQTT_CREDENTIALS.GENERATION.eq(M_AGENTS.GENERATION)))
                .where(T_BATCH_EXECUTION_REQUESTS.EXECUTION_ID.eq(executionId))
                .and(M_AGENTS.STATUS.eq(REGISTERED.getStoredName())).and(M_DEVICES.STATUS.ne("0"))
                .and(T_AGENT_MQTT_CREDENTIALS.SYNC_STATUS.eq(AgentMqttStatus.READY.getStoredName())).fetchOne(T_AGENT_MQTT_CREDENTIALS.CLIENT_ID);
    }

    public List<Record> registeredAgents() {
        return dsl.select(M_AGENTS.fields()).from(M_AGENTS).where(M_AGENTS.STATUS.eq(REGISTERED.getStoredName())).fetch();
    }

    public void create(UUID agentId, long generation, String clientId, String encryptedPassword) {
        dsl.insertInto(T_AGENT_MQTT_CREDENTIALS).set(T_AGENT_MQTT_CREDENTIALS.AGENT_ID, agentId)
                .set(T_AGENT_MQTT_CREDENTIALS.GENERATION, generation).set(T_AGENT_MQTT_CREDENTIALS.CLIENT_ID, clientId)
                .set(T_AGENT_MQTT_CREDENTIALS.ENCRYPTED_PASSWORD, encryptedPassword)
                .onConflict(T_AGENT_MQTT_CREDENTIALS.AGENT_ID, T_AGENT_MQTT_CREDENTIALS.GENERATION).doNothing().execute();
    }

    public List<Record> pending() {
        return dsl.select(List.of(T_AGENT_MQTT_CREDENTIALS.AGENT_ID, T_AGENT_MQTT_CREDENTIALS.GENERATION, M_AGENTS.DEVICE_ID))
                .from(T_AGENT_MQTT_CREDENTIALS).innerJoin(M_AGENTS).on(M_AGENTS.ID.eq(T_AGENT_MQTT_CREDENTIALS.AGENT_ID))
                .innerJoin(M_DEVICES).on(M_DEVICES.ID.eq(M_AGENTS.DEVICE_ID))
                .where(T_AGENT_MQTT_CREDENTIALS.SYNC_STATUS.eq(AgentMqttStatus.PENDING.getStoredName())
                        .or(T_AGENT_MQTT_CREDENTIALS.SYNC_STATUS.eq(AgentMqttStatus.READY.getStoredName()).and(
                                M_AGENTS.GENERATION.ne(T_AGENT_MQTT_CREDENTIALS.GENERATION)
                                        .or(M_AGENTS.STATUS.ne(REGISTERED.getStoredName())).or(M_DEVICES.STATUS.eq("0")))))
                .orderBy(T_AGENT_MQTT_CREDENTIALS.GENERATION).limit(100).fetch();
    }

    public Record lockDevice(long deviceId) {
        return dsl.select(M_DEVICES.STATUS).from(M_DEVICES).where(M_DEVICES.ID.eq(deviceId)).forUpdate().fetchOne();
    }

    public Record lockAgent(UUID agentId) {
        return dsl.selectFrom(M_AGENTS).where(M_AGENTS.ID.eq(agentId)).forUpdate().fetchOne();
    }

    public Record lockCredentials(UUID agentId, long generation) {
        return dsl.selectFrom(T_AGENT_MQTT_CREDENTIALS).where(T_AGENT_MQTT_CREDENTIALS.AGENT_ID.eq(agentId))
                .and(T_AGENT_MQTT_CREDENTIALS.GENERATION.eq(generation)).forUpdate().fetchOne();
    }

    public void markStatus(String clientId, String status) {
        dsl.update(T_AGENT_MQTT_CREDENTIALS).set(T_AGENT_MQTT_CREDENTIALS.SYNC_STATUS, status)
                .set(T_AGENT_MQTT_CREDENTIALS.UPDATED_AT, currentOffsetDateTime())
                .where(T_AGENT_MQTT_CREDENTIALS.CLIENT_ID.eq(clientId)).execute();
    }

    public Integer countUnrevokedOtherGenerations(UUID agentId, long generation) {
        return dsl.selectCount().from(T_AGENT_MQTT_CREDENTIALS)
                .where(T_AGENT_MQTT_CREDENTIALS.AGENT_ID.eq(agentId))
                .and(T_AGENT_MQTT_CREDENTIALS.GENERATION.ne(generation))
                .and(T_AGENT_MQTT_CREDENTIALS.SYNC_STATUS.ne(AgentMqttStatus.REVOKED.getStoredName())).fetchOne(0, Integer.class);
    }
}
