package jp.xoki.web.zunda.smarthomewebserverbackend.common.config;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.GatewayRateLimiterConstants.WINDOW_MILLIS;

/**
 * Gateway API向けのインメモリレート制限を行うコンポーネント。
 *
 * <p>制限キーごとに1分間の固定ウィンドウを持ち、設定された上限回数まで
 * リクエストを許可する。アプリケーションプロセス内で状態を保持するため、
 * 複数インスタンス構成ではインスタンスごとに制限が独立する。</p>
 */
@Component
public class GatewayRateLimiter {


	/** 1分あたりの許可リクエスト数などを保持するGateway設定。 */
	private final GatewayProperties gatewayProperties;

	/** ウィンドウ開始時刻や再試行時間を算出するための時計。 */
	private final Clock clock;

	/** 制限キーごとのレート制限状態を保持するインメモリストア。 */
	private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

	/**
	 * 任意の時計を利用するレートリミッターを生成する。
	 *
	 * <p>本番では共通Clock、単体テストでは時間経過を制御するClockを注入する。</p>
	 *
	 * @param gatewayProperties Gateway関連設定
	 * @param clock レート制限ウィンドウの時刻判定に使用する時計
	 */
	public GatewayRateLimiter(GatewayProperties gatewayProperties, Clock clock) {
		this.gatewayProperties = gatewayProperties;
		this.clock = clock;
	}

	/**
	 * 指定された制限キーのリクエスト可否を判定する。
	 *
	 * @param key APIキーまたは送信元IPを含む制限キー
	 * @return レート制限の判定結果
	 */
	public RateLimitResult consume(String key) {
		GatewayProperties.RateLimit rateLimit = gatewayProperties.getRateLimit();
		int limit = Math.max(rateLimit.getPerMinute(), 1);
		long now = clock.millis();
		Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket(now, limit));

		synchronized (bucket) {

			// 現在の固定ウィンドウを過ぎている場合は、新しい1分ウィンドウとして残数を初期化する。
			if (now - bucket.windowStartedAt >= WINDOW_MILLIS) {
				bucket.windowStartedAt = now;
				bucket.remaining = limit;
			}

			// 残数がない場合はリクエストを拒否し、次のウィンドウまでの秒数を返す。
			if (bucket.remaining <= 0) {
				long retryAfterMillis = WINDOW_MILLIS - (now - bucket.windowStartedAt);
				long retryAfterSeconds = Math.max(1L, (long) Math.ceil(retryAfterMillis / 1000.0));
				return new RateLimitResult(false, limit, 0, retryAfterSeconds);
			}

			// 許可するリクエストは残数を1つ消費する。
			bucket.remaining--;
			return new RateLimitResult(true, limit, bucket.remaining, 0);
		}
	}

	/**
	 * レート制限の消費結果を表す値オブジェクト。
	 *
	 * @param allowed リクエストを許可する場合はtrue
	 * @param limit 1分間に許可される最大リクエスト数
	 * @param remaining 現在のウィンドウ内で残っているリクエスト数
	 * @param retryAfterSeconds 制限超過時に再試行まで待つべき秒数
	 */
	public record RateLimitResult(
			boolean allowed,
			int limit,
			int remaining,
			long retryAfterSeconds
	) {
	}

	/** 制限キーごとの固定ウィンドウ状態を保持する内部クラス。 */
	private static class Bucket {

		/** 現在の固定ウィンドウが開始したエポックミリ秒。 */
		private long windowStartedAt;

		/** 現在の固定ウィンドウ内で残っている許可リクエスト数。 */
		private int remaining;

		/**
		 * 固定ウィンドウ状態を生成する。
		 *
		 * @param windowStartedAt ウィンドウ開始時刻のエポックミリ秒
		 * @param remaining ウィンドウ内の残りリクエスト数
		 */
		Bucket(long windowStartedAt, int remaining) {
			this.windowStartedAt = windowStartedAt;
			this.remaining = remaining;
		}
	}
}
