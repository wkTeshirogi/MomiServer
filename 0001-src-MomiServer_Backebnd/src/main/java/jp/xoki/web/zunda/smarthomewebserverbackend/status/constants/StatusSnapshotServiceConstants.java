package jp.xoki.web.zunda.smarthomewebserverbackend.status.constants;

/** StatusSnapshotServiceで使用する固定値。カテゴリ内の利用元から参照する。 */
public final class StatusSnapshotServiceConstants {

	/** 実測値の鮮度。有効なAgentのハートビート期限とは別に判定する。 */
	public static final int METRICS_FRESHNESS_MINUTES = 3;

	public static final String CPU_PERCENT_VALUE = "cpu_percent";
	public static final String MEMORY_PERCENT_VALUE = "memory_percent";
	public static final String METRIC_COLLECTED_AT_VALUE = "metric_collected_at";
	public static final String MAINTENANCE_VALUE = "maintenance";
	public static final String OFFLINE_VALUE = "offline";
	public static final String UNKNOWN_VALUE = "unknown";
	public static final String ONLINE_VALUE = "online";


	private StatusSnapshotServiceConstants() {
	}
}
