package jp.xoki.web.zunda.smarthomewebserverbackend.agent.repository;

import static jp.xoki.web.zunda.jooq.Tables.M_AGENTS;
import static jp.xoki.web.zunda.smarthomewebserverbackend.agent.model.AgentRegistrationStatus.REGISTERED;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.model.BatchExecutionStatus;
import static jp.xoki.web.zunda.jooq.Tables.T_BATCH_EXECUTION_REQUESTS;
import static org.jooq.impl.DSL.currentLocalDateTime;
import static org.jooq.impl.DSL.currentOffsetDateTime;
import static org.jooq.impl.DSL.when;

import java.util.List;
import java.util.UUID;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.model.AgentPrincipal;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

/** 認証済みAgentの同期対象と接続状態を保存・取得する。 */
@Repository
@RequiredArgsConstructor
public class AgentRuntimeRepository {
    private final DSLContext dsl;

    public String operatingSystem(UUID agentId) {
        return dsl.select(M_AGENTS.OS).from(M_AGENTS).where(M_AGENTS.ID.eq(agentId)).fetchOne(M_AGENTS.OS);
    }

    public List<UUID> executionIds(AgentPrincipal principal) {
        return dsl.select(T_BATCH_EXECUTION_REQUESTS.EXECUTION_ID).from(T_BATCH_EXECUTION_REQUESTS)
                .where(T_BATCH_EXECUTION_REQUESTS.TARGET_AGENT_ID.eq(principal.deviceId()))
                .and(T_BATCH_EXECUTION_REQUESTS.TARGET_AGENT_GENERATION.eq(principal.generation()))
                .and(T_BATCH_EXECUTION_REQUESTS.EXECUTION_STATUS.in(BatchExecutionStatus.QUEUED.getStoredName(),
                        BatchExecutionStatus.DISPATCHED.getStoredName(), BatchExecutionStatus.RUNNING.getStoredName()))
                .and(T_BATCH_EXECUTION_REQUESTS.SCHEDULED_FIRE_TIME.isNull()
                        .or(T_BATCH_EXECUTION_REQUESTS.SCHEDULED_FIRE_TIME.le(currentLocalDateTime())))
                .orderBy(T_BATCH_EXECUTION_REQUESTS.REQUESTED_AT).limit(1000)
                .fetch(T_BATCH_EXECUTION_REQUESTS.EXECUTION_ID);
    }

    public void heartbeat(AgentPrincipal principal, boolean mqttConnected) {
        dsl.update(M_AGENTS).set(M_AGENTS.HEARTBEAT_AT, currentOffsetDateTime())
                .set(M_AGENTS.MQTT_CONNECTED, mqttConnected).where(M_AGENTS.ID.eq(principal.agentId()))
                .and(M_AGENTS.GENERATION.eq(principal.generation())).and(M_AGENTS.STATUS.eq(REGISTERED.getStoredName())).execute();
    }

    public void unconfirmed(UUID executionId, String reason) {
        dsl.update(T_BATCH_EXECUTION_REQUESTS).set(T_BATCH_EXECUTION_REQUESTS.CONFIRMATION_STATUS,
                        when(T_BATCH_EXECUTION_REQUESTS.CANCEL_REQUESTED_AT.isNotNull(), "stop_unconfirmed")
                                .when(T_BATCH_EXECUTION_REQUESTS.EXECUTION_STATUS.eq(BatchExecutionStatus.RUNNING.getStoredName()), "result_unconfirmed")
                                .otherwise("start_unconfirmed"))
                .set(T_BATCH_EXECUTION_REQUESTS.MESSAGE, reason)
                .where(T_BATCH_EXECUTION_REQUESTS.EXECUTION_ID.eq(executionId))
                .and(T_BATCH_EXECUTION_REQUESTS.EXECUTION_STATUS.in(BatchExecutionStatus.QUEUED.getStoredName(),
                        BatchExecutionStatus.DISPATCHED.getStoredName(), BatchExecutionStatus.RUNNING.getStoredName())).execute();
    }
}
