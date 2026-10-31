package jp.xoki.web.zunda.smarthomewebserverbackend.common.constants;

/** GatewayRateLimiterで使用する固定値。カテゴリ内の利用元から参照する。 */
public final class GatewayRateLimiterConstants {

	/** レート制限の固定ウィンドウ幅。現在は1分単位で制限する。 */
	public static final long WINDOW_MILLIS = 60_000L;

	private GatewayRateLimiterConstants() {
	}
}
