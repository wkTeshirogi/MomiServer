package jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants;

/** AgentSecretsで使用する固定値。カテゴリ内の利用元から参照する。 */
public final class AgentSecretsConstants {

    public static final int TOKEN_BYTES = 32;

    public static final int MIN_ACCESS_MINUTES = 6;

    public static final int IV_BYTES = 12;

    public static final int GCM_TAG_BITS = 128;

    public static final int MAX_NONCE_LENGTH = 100;

	private AgentSecretsConstants() {
	}
}
