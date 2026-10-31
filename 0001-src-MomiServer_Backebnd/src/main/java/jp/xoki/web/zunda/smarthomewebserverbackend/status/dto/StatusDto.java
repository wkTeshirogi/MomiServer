package jp.xoki.web.zunda.smarthomewebserverbackend.status.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/** マシン・サービス状態配信で使用するDTO群。 */
public final class StatusDto {

	private StatusDto() {
	}

	/** WebSocket配信対象。 */
	public enum Channel {
		MACHINES("machines", "/ws/machines"),
		SERVICES("services", "/ws/services");

		private final String value;
		private final String path;

		Channel(String value, String path) {
			this.value = value;
			this.path = path;
		}

		public String value() {
			return value;
		}

		public String path() {
			return path;
		}

		public static Channel from(String value) {
			for (Channel channel : values()) {
				if (channel.value.equalsIgnoreCase(value)) {
					return channel;
				}
			}
			throw new IllegalArgumentException("Unknown status channel: " + value);
		}
	}

	/** ストレージ使用量。Agentからのメトリクス連携時に使用する。 */
	public record StorageStatus(long id, String name, long max, long value) {
	}

	/** マシン状態画面へ返す1端末分の状態。 */
	public record MachineStatus(
			long id,
			String name,
			String status,
			int cpu,
			int mem,
			int healthScore,
			Integer cpuTemp,
			Integer gpuTemp,
			List<StorageStatus> storage,
			String worktime,
			String version,
			String ipAddress,
			String hostname,
			String location,
			boolean metricsAvailable,
			OffsetDateTime observedAt
	) {
		public MachineStatus {
			storage = storage == null ? null : Collections.unmodifiableList(new ArrayList<>(storage));
		}
	}

	/** サービス状態画面へ返す1サービス分の状態。 */
	public record ServiceStatus(
			long id,
			String name,
			String status,
			int cpu,
			int mem,
			String worktime,
			String version,
			String players,
			String address,
			int custom_now,
			int custom_max,
			String custom_name,
			String pid,
			boolean metricsAvailable,
			OffsetDateTime observedAt
	) {
	}

	/** マシン状態の全件更新メッセージ。 */
	public record MachineUpdateMessage(
			String type,
			List<MachineStatus> machines,
			OffsetDateTime generated_at
	) {
		public MachineUpdateMessage {
			machines = machines == null ? null : Collections.unmodifiableList(new ArrayList<>(machines));
		}
	}

	/** サービス状態の全件更新メッセージ。 */
	public record ServiceUpdateMessage(
			String type,
			List<ServiceStatus> services,
			OffsetDateTime generated_at
	) {
		public ServiceUpdateMessage {
			services = services == null ? null : Collections.unmodifiableList(new ArrayList<>(services));
		}
	}

	/** WebSocket接続チケット発行要求。 */
	public record TicketRequest(
			@NotBlank
			@Pattern(regexp = "machines|services")
			String channel
	) {
	}

	/** WebSocket接続チケット発行結果。 */
	public record TicketResponse(String ticket, String websocket_path, OffsetDateTime expires_at) {
	}
}
