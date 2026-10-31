package jp.xoki.web.zunda.smarthomewebserverbackend.agent.repository;

import static jp.xoki.web.zunda.jooq.Tables.M_AGENTS;
import static jp.xoki.web.zunda.jooq.Tables.M_SERVICES;
import static jp.xoki.web.zunda.jooq.Tables.T_AGENT_MACHINE_STATUS;
import static jp.xoki.web.zunda.jooq.Tables.T_AGENT_SERVICE_STATUS;
import static jp.xoki.web.zunda.jooq.Tables.T_AGENT_STORAGE_STATUS;
import static org.jooq.impl.DSL.currentOffsetDateTime;
import static org.jooq.impl.DSL.excluded;
import static org.jooq.impl.DSL.lower;

import jp.xoki.web.zunda.smarthomewebserverbackend.agent.dto.AgentStatusDto.ServiceSnapshot;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.dto.AgentStatusDto.ServiceTarget;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.dto.AgentStatusDto.SnapshotRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.dto.AgentStatusDto.StorageSnapshot;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.model.AgentPrincipal;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/** Agent状態の監視対象取得と最新スナップショット保存を行うRepository。 */
@Repository
@RequiredArgsConstructor
public class AgentStatusRepository {
    private final DSLContext dsl;

    public List<ServiceTarget> findTargets(long deviceId) {
        return dsl.select(M_SERVICES.ID, M_SERVICES.SERVICE_NAME).from(M_SERVICES)
                .where(M_SERVICES.TARGET_MACHINE.eq(deviceId)).and(lower(M_SERVICES.STATUS).ne("deleted"))
                .orderBy(M_SERVICES.ID).fetch(row -> new ServiceTarget(row.value1(), row.value2()));
    }

    public boolean lockRegisteredAgent(AgentPrincipal principal) {
        return dsl.select(M_AGENTS.ID).from(M_AGENTS).where(M_AGENTS.ID.eq(principal.agentId()))
                .and(M_AGENTS.DEVICE_ID.eq(principal.deviceId())).and(M_AGENTS.GENERATION.eq(principal.generation()))
                .and(M_AGENTS.STATUS.eq("registered")).forShare().fetchOne() != null;
    }

    public Set<Long> findTargetIds(long deviceId) {
        return Set.copyOf(dsl.select(M_SERVICES.ID).from(M_SERVICES)
                .where(M_SERVICES.TARGET_MACHINE.eq(deviceId)).and(lower(M_SERVICES.STATUS).ne("deleted"))
                .fetch(M_SERVICES.ID));
    }

    public void save(AgentPrincipal principal, SnapshotRequest request) {
        int machineRows = dsl.insertInto(T_AGENT_MACHINE_STATUS)
                .set(T_AGENT_MACHINE_STATUS.AGENT_ID, principal.agentId())
                .set(T_AGENT_MACHINE_STATUS.GENERATION, principal.generation())
                .set(T_AGENT_MACHINE_STATUS.COLLECTED_AT, request.collected_at())
                .set(T_AGENT_MACHINE_STATUS.CPU_PERCENT, decimal(request.cpu_percent()))
                .set(T_AGENT_MACHINE_STATUS.MEMORY_PERCENT, decimal(request.memory_percent()))
                .set(T_AGENT_MACHINE_STATUS.CPU_TEMPERATURE_C, decimal(request.cpu_temperature_c()))
                .set(T_AGENT_MACHINE_STATUS.GPU_TEMPERATURE_C, decimal(request.gpu_temperature_c()))
                .set(T_AGENT_MACHINE_STATUS.UPTIME_SECONDS, request.uptime_seconds())
                .onConflict(T_AGENT_MACHINE_STATUS.AGENT_ID).doUpdate()
                .set(T_AGENT_MACHINE_STATUS.GENERATION, excluded(T_AGENT_MACHINE_STATUS.GENERATION))
                .set(T_AGENT_MACHINE_STATUS.COLLECTED_AT, excluded(T_AGENT_MACHINE_STATUS.COLLECTED_AT))
                .set(T_AGENT_MACHINE_STATUS.CPU_PERCENT, excluded(T_AGENT_MACHINE_STATUS.CPU_PERCENT))
                .set(T_AGENT_MACHINE_STATUS.MEMORY_PERCENT, excluded(T_AGENT_MACHINE_STATUS.MEMORY_PERCENT))
                .set(T_AGENT_MACHINE_STATUS.CPU_TEMPERATURE_C, excluded(T_AGENT_MACHINE_STATUS.CPU_TEMPERATURE_C))
                .set(T_AGENT_MACHINE_STATUS.GPU_TEMPERATURE_C, excluded(T_AGENT_MACHINE_STATUS.GPU_TEMPERATURE_C))
                .set(T_AGENT_MACHINE_STATUS.UPTIME_SECONDS, excluded(T_AGENT_MACHINE_STATUS.UPTIME_SECONDS))
                .set(T_AGENT_MACHINE_STATUS.UPDATED_AT, currentOffsetDateTime())
                .where(excluded(T_AGENT_MACHINE_STATUS.GENERATION).ne(T_AGENT_MACHINE_STATUS.GENERATION)
                        .or(excluded(T_AGENT_MACHINE_STATUS.COLLECTED_AT).ge(T_AGENT_MACHINE_STATUS.COLLECTED_AT)))
                .execute();
        // 古い同世代の計測値を拒否した場合、子のスナップショットも置き換えない。
        if (machineRows == 0) {
            return;
        }
        dsl.deleteFrom(T_AGENT_STORAGE_STATUS).where(T_AGENT_STORAGE_STATUS.AGENT_ID.eq(principal.agentId())).execute();
        for (StorageSnapshot storage : request.storage()) {
            dsl.insertInto(T_AGENT_STORAGE_STATUS).set(T_AGENT_STORAGE_STATUS.AGENT_ID, principal.agentId())
                    .set(T_AGENT_STORAGE_STATUS.STORAGE_KEY, storage.storage_key())
                    .set(T_AGENT_STORAGE_STATUS.DISPLAY_NAME, storage.display_name())
                    .set(T_AGENT_STORAGE_STATUS.TOTAL_BYTES, storage.total_bytes())
                    .set(T_AGENT_STORAGE_STATUS.USED_BYTES, storage.used_bytes())
                    .set(T_AGENT_STORAGE_STATUS.COLLECTED_AT, request.collected_at()).execute();
        }
        dsl.deleteFrom(T_AGENT_SERVICE_STATUS).where(T_AGENT_SERVICE_STATUS.AGENT_ID.eq(principal.agentId())).execute();
        for (ServiceSnapshot service : request.services()) {
            dsl.insertInto(T_AGENT_SERVICE_STATUS).set(T_AGENT_SERVICE_STATUS.SERVICE_ID, service.service_id())
                    .set(T_AGENT_SERVICE_STATUS.AGENT_ID, principal.agentId())
                    .set(T_AGENT_SERVICE_STATUS.GENERATION, principal.generation())
                    .set(T_AGENT_SERVICE_STATUS.COLLECTED_AT, request.collected_at())
                    .set(T_AGENT_SERVICE_STATUS.STATUS, service.status()).set(T_AGENT_SERVICE_STATUS.PROCESS_ID, service.process_id())
                    .set(T_AGENT_SERVICE_STATUS.UPTIME_SECONDS, service.uptime_seconds())
                    .set(T_AGENT_SERVICE_STATUS.CPU_PERCENT, decimal(service.cpu_percent()))
                    .set(T_AGENT_SERVICE_STATUS.MEMORY_PERCENT, decimal(service.memory_percent()))
                    .onConflict(T_AGENT_SERVICE_STATUS.SERVICE_ID).doUpdate()
                    .set(T_AGENT_SERVICE_STATUS.AGENT_ID, excluded(T_AGENT_SERVICE_STATUS.AGENT_ID))
                    .set(T_AGENT_SERVICE_STATUS.GENERATION, excluded(T_AGENT_SERVICE_STATUS.GENERATION))
                    .set(T_AGENT_SERVICE_STATUS.COLLECTED_AT, excluded(T_AGENT_SERVICE_STATUS.COLLECTED_AT))
                    .set(T_AGENT_SERVICE_STATUS.STATUS, excluded(T_AGENT_SERVICE_STATUS.STATUS))
                    .set(T_AGENT_SERVICE_STATUS.PROCESS_ID, excluded(T_AGENT_SERVICE_STATUS.PROCESS_ID))
                    .set(T_AGENT_SERVICE_STATUS.UPTIME_SECONDS, excluded(T_AGENT_SERVICE_STATUS.UPTIME_SECONDS))
                    .set(T_AGENT_SERVICE_STATUS.CPU_PERCENT, excluded(T_AGENT_SERVICE_STATUS.CPU_PERCENT))
                    .set(T_AGENT_SERVICE_STATUS.MEMORY_PERCENT, excluded(T_AGENT_SERVICE_STATUS.MEMORY_PERCENT))
                    .set(T_AGENT_SERVICE_STATUS.UPDATED_AT, currentOffsetDateTime()).execute();
        }
    }
    private BigDecimal decimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }
}
