package jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants;

/** MosquittoControlで使用する固定値。カテゴリ内の利用元から参照する。 */
public final class MosquittoControlConstants {

    public static final String TOPIC = "$CONTROL/dynamic-security/v1";

    public static final int TIMEOUT_SECONDS = 10;

	private MosquittoControlConstants() {
	}
}
