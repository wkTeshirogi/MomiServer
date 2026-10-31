package jp.xoki.web.zunda.smarthomewebserverbackend.status.repository;

import static jp.xoki.web.zunda.jooq.Tables.M_AGENTS;
import static jp.xoki.web.zunda.jooq.Tables.M_DEVICES;
import static jp.xoki.web.zunda.jooq.Tables.M_SERVICES;
import static jp.xoki.web.zunda.jooq.Tables.T_AGENT_MACHINE_STATUS;
import static jp.xoki.web.zunda.jooq.Tables.T_AGENT_SERVICE_STATUS;
import static jp.xoki.web.zunda.jooq.Tables.T_AGENT_STORAGE_STATUS;
import static org.jooq.impl.DSL.lower;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import java.util.List;

/** 状態画面に必要なマスタ情報とAgent接続情報を取得するRepository。 */
@Repository
@RequiredArgsConstructor
public class StatusSnapshotRepository {
    private final DSLContext dsl;

    /** 有効な端末と、その端末に登録されたAgentの最終状態を取得する。 */
    public List<Record> findMachines() {
        return dsl.select(List.of(M_DEVICES.ID, M_DEVICES.DEVICE_NAME, M_DEVICES.ADDRESS, M_DEVICES.LOCATION, M_DEVICES.STATUS,
                M_AGENTS.HOSTNAME, M_AGENTS.OS, M_AGENTS.AGENT_VERSION, M_AGENTS.HEARTBEAT_AT,
                M_AGENTS.ID.as("agent_id"), M_AGENTS.STATUS.as("agent_status"), M_AGENTS.GENERATION.as("agent_generation"),
                T_AGENT_MACHINE_STATUS.GENERATION.as("metric_generation"), T_AGENT_MACHINE_STATUS.COLLECTED_AT.as("metric_collected_at"),
                T_AGENT_MACHINE_STATUS.CPU_PERCENT, T_AGENT_MACHINE_STATUS.MEMORY_PERCENT,
                T_AGENT_MACHINE_STATUS.CPU_TEMPERATURE_C, T_AGENT_MACHINE_STATUS.GPU_TEMPERATURE_C,
                T_AGENT_MACHINE_STATUS.UPTIME_SECONDS))
                .from(M_DEVICES).leftJoin(M_AGENTS).on(M_AGENTS.DEVICE_ID.eq(M_DEVICES.ID))
                .leftJoin(T_AGENT_MACHINE_STATUS).on(T_AGENT_MACHINE_STATUS.AGENT_ID.eq(M_AGENTS.ID))
                .where(M_DEVICES.STATUS.ne("0")).orderBy(M_DEVICES.ID).fetch();
    }

    /** 現在の登録世代に一致するAgentのストレージ状態を取得する。 */
    public List<Record> findStorage() {
        return dsl.select(List.of(M_DEVICES.ID.as("device_id"), T_AGENT_STORAGE_STATUS.AGENT_ID,
                T_AGENT_STORAGE_STATUS.STORAGE_KEY, T_AGENT_STORAGE_STATUS.DISPLAY_NAME,
                T_AGENT_STORAGE_STATUS.TOTAL_BYTES, T_AGENT_STORAGE_STATUS.USED_BYTES, T_AGENT_STORAGE_STATUS.COLLECTED_AT))
                .from(T_AGENT_STORAGE_STATUS).innerJoin(M_AGENTS)
                .on(M_AGENTS.ID.eq(T_AGENT_STORAGE_STATUS.AGENT_ID).and(M_AGENTS.STATUS.eq("registered")))
                .innerJoin(T_AGENT_MACHINE_STATUS).on(T_AGENT_MACHINE_STATUS.AGENT_ID.eq(M_AGENTS.ID)
                        .and(T_AGENT_MACHINE_STATUS.GENERATION.eq(M_AGENTS.GENERATION)))
                .innerJoin(M_DEVICES).on(M_DEVICES.ID.eq(M_AGENTS.DEVICE_ID).and(M_DEVICES.STATUS.ne("0")))
                .where(T_AGENT_STORAGE_STATUS.COLLECTED_AT.eq(T_AGENT_MACHINE_STATUS.COLLECTED_AT))
                .orderBy(M_DEVICES.ID, T_AGENT_STORAGE_STATUS.STORAGE_KEY).fetch();
    }

    /** 論理削除されていないサービスと対象端末のAgent状態を取得する。 */
    public List<Record> findServices() {
        return dsl.select(List.of(M_SERVICES.ID, M_SERVICES.SERVICE_NAME, M_SERVICES.VIEW_NAME, M_SERVICES.STATUS,
                M_DEVICES.ADDRESS, M_DEVICES.STATUS.as("device_status"), M_AGENTS.AGENT_VERSION, M_AGENTS.HEARTBEAT_AT,
                M_AGENTS.STATUS.as("agent_status"), M_AGENTS.GENERATION.as("agent_generation"),
                T_AGENT_SERVICE_STATUS.GENERATION.as("metric_generation"), T_AGENT_SERVICE_STATUS.COLLECTED_AT.as("metric_collected_at"),
                T_AGENT_SERVICE_STATUS.STATUS.as("runtime_status"), T_AGENT_SERVICE_STATUS.PROCESS_ID,
                T_AGENT_SERVICE_STATUS.UPTIME_SECONDS, T_AGENT_SERVICE_STATUS.CPU_PERCENT, T_AGENT_SERVICE_STATUS.MEMORY_PERCENT))
                .from(M_SERVICES).leftJoin(M_DEVICES).on(M_DEVICES.ID.eq(M_SERVICES.TARGET_MACHINE))
                .leftJoin(M_AGENTS).on(M_AGENTS.DEVICE_ID.eq(M_DEVICES.ID))
                .leftJoin(T_AGENT_SERVICE_STATUS).on(T_AGENT_SERVICE_STATUS.SERVICE_ID.eq(M_SERVICES.ID)
                        .and(T_AGENT_SERVICE_STATUS.AGENT_ID.eq(M_AGENTS.ID)))
                .where(lower(M_SERVICES.STATUS).ne("deleted")).orderBy(M_SERVICES.ID).fetch();
    }
}
