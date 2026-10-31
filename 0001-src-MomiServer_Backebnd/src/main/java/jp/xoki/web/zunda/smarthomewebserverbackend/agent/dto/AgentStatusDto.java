package jp.xoki.web.zunda.smarthomewebserverbackend.agent.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/** Agentが収集する端末・サービス状態のDTO群。 */
public final class AgentStatusDto {

	private AgentStatusDto() {
	}

	/** Agentが監視するサービス定義。 */
	public record ServiceTarget(long id, String service_name) {
	}

	/** 監視対象サービス一覧。 */
	public record TargetResponse(List<ServiceTarget> services) {
		public TargetResponse {
			services = services == null ? null : Collections.unmodifiableList(new ArrayList<>(services));
		}
	}

	/** ストレージ状態。 */
	public record StorageSnapshot(
			@NotBlank @Size(max = 500) String storage_key,
			@NotBlank @Size(max = 500) String display_name,
			@PositiveOrZero long total_bytes,
			@PositiveOrZero long used_bytes
	) {
	}

	/** OSサービスと対応プロセスの状態。 */
	public record ServiceSnapshot(
			@Positive long service_id,
			@NotBlank @Pattern(regexp = "online|offline|unknown") String status,
			@Positive Long process_id,
			@PositiveOrZero long uptime_seconds,
			@DecimalMin("0.0") @DecimalMax("100.0") double cpu_percent,
			@DecimalMin("0.0") @DecimalMax("100.0") double memory_percent
	) {
	}

	/** Agentが一定間隔で送信する最新状態一式。 */
	public record SnapshotRequest(
			@NotNull OffsetDateTime collected_at,
			@DecimalMin("0.0") @DecimalMax("100.0") double cpu_percent,
			@DecimalMin("0.0") @DecimalMax("100.0") double memory_percent,
			@DecimalMin("-100.0") @DecimalMax("250.0") Double cpu_temperature_c,
			@DecimalMin("-100.0") @DecimalMax("250.0") Double gpu_temperature_c,
			@PositiveOrZero long uptime_seconds,
			@NotNull @Size(max = 128) List<@Valid StorageSnapshot> storage,
			@NotNull @Size(max = 1000) List<@Valid ServiceSnapshot> services
	) {
		public SnapshotRequest {
			storage = storage == null ? null : Collections.unmodifiableList(new ArrayList<>(storage));
			services = services == null ? null : Collections.unmodifiableList(new ArrayList<>(services));
		}
	}
}
