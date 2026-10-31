package jp.xoki.web.zunda.smarthomewebserverbackend.status.service;

import static jp.xoki.web.zunda.smarthomewebserverbackend.status.constants.StatusSnapshotServiceConstants.CPU_PERCENT_VALUE;
import static jp.xoki.web.zunda.smarthomewebserverbackend.status.constants.StatusSnapshotServiceConstants.MAINTENANCE_VALUE;
import static jp.xoki.web.zunda.smarthomewebserverbackend.status.constants.StatusSnapshotServiceConstants.MEMORY_PERCENT_VALUE;
import static jp.xoki.web.zunda.smarthomewebserverbackend.status.constants.StatusSnapshotServiceConstants.METRIC_COLLECTED_AT_VALUE;
import static jp.xoki.web.zunda.smarthomewebserverbackend.status.constants.StatusSnapshotServiceConstants.OFFLINE_VALUE;
import static jp.xoki.web.zunda.smarthomewebserverbackend.status.constants.StatusSnapshotServiceConstants.ONLINE_VALUE;
import static jp.xoki.web.zunda.smarthomewebserverbackend.status.constants.StatusSnapshotServiceConstants.UNKNOWN_VALUE;


import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import jp.xoki.web.zunda.smarthomewebserverbackend.status.dto.StatusDto.MachineStatus;
import jp.xoki.web.zunda.smarthomewebserverbackend.status.dto.StatusDto.MachineUpdateMessage;
import jp.xoki.web.zunda.smarthomewebserverbackend.status.dto.StatusDto.ServiceStatus;
import jp.xoki.web.zunda.smarthomewebserverbackend.status.dto.StatusDto.ServiceUpdateMessage;
import jp.xoki.web.zunda.smarthomewebserverbackend.status.dto.StatusDto.StorageStatus;
import jp.xoki.web.zunda.smarthomewebserverbackend.status.repository.StatusSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.Record;
import org.springframework.stereotype.Service;

import static jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants.AgentRuntimeConstants.HEARTBEAT_OFFLINE_MINUTES;
import static jp.xoki.web.zunda.smarthomewebserverbackend.agent.model.AgentRegistrationStatus.REGISTERED;
import static jp.xoki.web.zunda.smarthomewebserverbackend.status.constants.StatusSnapshotServiceConstants.METRICS_FRESHNESS_MINUTES;
import java.time.Clock;

/** マスタ、Agentハートビート、最新実測値から画面用状態スナップショットを生成するService。 */
@Service
@RequiredArgsConstructor
public class StatusSnapshotService {

	private final StatusSnapshotRepository repository;

	private final Clock clock;

	public List<MachineStatus> machines() {
		Map<Long, List<StorageStatus>> storage = storageByDevice();
		return repository.findMachines().stream()
				.map(row -> toMachine(row, storage.getOrDefault(row.get("id", Long.class), List.of())))
				.toList();
	}

	public List<ServiceStatus> services() {
		return repository.findServices().stream().map(this::toService).toList();
	}

	public MachineUpdateMessage machineMessage() {
		return new MachineUpdateMessage("machine_update", machines(), OffsetDateTime.now(clock));
	}

	public ServiceUpdateMessage serviceMessage() {
		return new ServiceUpdateMessage("service_update", services(), OffsetDateTime.now(clock));
	}

	private MachineStatus toMachine(Record row, List<StorageStatus> storage) {
		OffsetDateTime heartbeat = registeredHeartbeat(row);
		String status = machineStatus(row.get("status", String.class), heartbeat, clock);
		boolean metricsAvailable = metricsAvailable(row);
		String os = text(row, "os");
		String agentVersion = text(row, "agent_version");
		String version = agentVersion;
		if (agentVersion.isBlank()) {
			version = os;
		} else if (!os.isBlank()) {
			version += " / " + os;
		}
		return new MachineStatus(
				row.get("id", Long.class),
				text(row, "device_name"),
				status,
				percent(row, CPU_PERCENT_VALUE, metricsAvailable),
				percent(row, MEMORY_PERCENT_VALUE, metricsAvailable),
				healthScore(row, metricsAvailable),
				temperature(row.get("cpu_temperature_c", Number.class), metricsAvailable),
				temperature(row.get("gpu_temperature_c", Number.class), metricsAvailable),
				metricsAvailable ? storage : List.of(),
				metricsAvailable ? formatUptime(row.get("uptime_seconds", Long.class)) : "",
				version,
				text(row, "address"),
				text(row, "hostname"),
				text(row, "location"),
				metricsAvailable,
				metricsAvailable ? row.get(METRIC_COLLECTED_AT_VALUE, OffsetDateTime.class) : heartbeat
		);
	}

	private ServiceStatus toService(Record row) {
		OffsetDateTime heartbeat = registeredHeartbeat(row);
		String machineStatus = machineStatus(row.get("device_status", String.class), heartbeat, clock);
		boolean metricsAvailable = metricsAvailable(row);
		String status = serviceStatus(row.get("status", String.class), machineStatus,
				metricsAvailable ? text(row, "runtime_status") : null);
		String name = text(row, "view_name");
		if (name.isBlank()) {
			name = text(row, "service_name");
		}
		return new ServiceStatus(
				row.get("id", Long.class),
				name,
				status,
				percent(row, CPU_PERCENT_VALUE, metricsAvailable),
				percent(row, MEMORY_PERCENT_VALUE, metricsAvailable),
				metricsAvailable ? formatUptime(row.get("uptime_seconds", Long.class)) : "",
				text(row, "agent_version"),
				"",
				text(row, "address"),
				0,
				0,
				"none",
				metricsAvailable && row.get("process_id", Long.class) != null
						? row.get("process_id", Long.class).toString() : "",
				metricsAvailable,
				metricsAvailable ? row.get(METRIC_COLLECTED_AT_VALUE, OffsetDateTime.class) : heartbeat
		);
	}

	static String machineStatus(String masterStatus, OffsetDateTime heartbeat, Clock clock) {
		if ("90".equals(masterStatus)) {
			return MAINTENANCE_VALUE;
		}
		if ("99".equals(masterStatus)) {
			return OFFLINE_VALUE;
		}
		if (heartbeat == null) {
			return UNKNOWN_VALUE;
		}
		return heartbeat.isAfter(OffsetDateTime.now(clock).minusMinutes(HEARTBEAT_OFFLINE_MINUTES))
				? ONLINE_VALUE : OFFLINE_VALUE;
	}

	static String serviceStatus(String masterStatus, String machineStatus) {
		return serviceStatus(masterStatus, machineStatus, null);
	}

	static String serviceStatus(String masterStatus, String machineStatus, String runtimeStatus) {
		String normalized = masterStatus == null ? "" : masterStatus.toLowerCase(Locale.ROOT);
		if (MAINTENANCE_VALUE.equals(normalized) || "stop".equals(normalized)) {
			return MAINTENANCE_VALUE;
		}
		if (!ONLINE_VALUE.equals(machineStatus)) {
			return machineStatus;
		}
		if (ONLINE_VALUE.equals(runtimeStatus) || OFFLINE_VALUE.equals(runtimeStatus) || UNKNOWN_VALUE.equals(runtimeStatus)) {
			return runtimeStatus;
		}
		if (ONLINE_VALUE.equals(normalized) || OFFLINE_VALUE.equals(normalized)) {
			return normalized;
		}
		return UNKNOWN_VALUE;
	}

	private String text(Record row, String field) {
		String value = row.get(field, String.class);
		return value == null ? "" : value;
	}

	private OffsetDateTime registeredHeartbeat(Record row) {
		return REGISTERED.getStoredName().equals(row.get("agent_status", String.class))
				? row.get("heartbeat_at", OffsetDateTime.class) : null;
	}

	private boolean metricsAvailable(Record row) {
		Long agentGeneration = row.get("agent_generation", Long.class);
		Long metricGeneration = row.get("metric_generation", Long.class);
		OffsetDateTime collectedAt = row.get(METRIC_COLLECTED_AT_VALUE, OffsetDateTime.class);
		return REGISTERED.getStoredName().equals(row.get("agent_status", String.class))
				&& Objects.equals(agentGeneration, metricGeneration)
				&& collectedAt != null
				&& collectedAt.isAfter(OffsetDateTime.now(clock).minusMinutes(METRICS_FRESHNESS_MINUTES));
	}

	private int percent(Record row, String field, boolean available) {
		Number value = available ? row.get(field, Number.class) : null;
		return value == null ? 0 : Math.clamp((int) Math.round(value.doubleValue()), 0, 100);
	}

	static Integer temperature(Number value, boolean available) {
		return available && value != null ? (int) Math.round(value.doubleValue()) : null;
	}

	private int healthScore(Record row, boolean available) {
		if (!available) {
			return 0;
		}
		return Math.clamp(100L - Math.max(percent(row, CPU_PERCENT_VALUE, true),
				percent(row, MEMORY_PERCENT_VALUE, true)), 0, 100);
	}

	private String formatUptime(Long seconds) {
		if (seconds == null || seconds < 0) {
			return "";
		}
		long days = seconds / 86400;
		long hours = seconds % 86400 / 3600;
		long minutes = seconds % 3600 / 60;
		return "%dd %02d:%02d".formatted(days, hours, minutes);
	}

	private Map<Long, List<StorageStatus>> storageByDevice() {
		Map<Long, List<StorageStatus>> result = new HashMap<>();
		for (Record row : repository.findStorage()) {
			long id = Integer.toUnsignedLong(Objects.hash(
					row.get("agent_id"), row.get("storage_key", String.class)));
			long mebibyte = 1024L * 1024L;
			StorageStatus value = new StorageStatus(
					id,
					text(row, "display_name") + " [MiB]",
					row.get("total_bytes", Long.class) / mebibyte,
					row.get("used_bytes", Long.class) / mebibyte);
			result.computeIfAbsent(row.get("device_id", Long.class), ignored -> new ArrayList<>()).add(value);
		}
		return result;
	}
}
