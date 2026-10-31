package jp.xoki.web.zunda.smarthomewebserverbackend.common.constants;

import java.util.regex.Pattern;

/** GatewayApiKeyAuthFilterで使用する固定値。カテゴリ内の利用元から参照する。 */
public final class GatewayApiKeyAuthFilterConstants {

	/** Gateway制御の対象とする外部連携APIパスの正規表現。 */
	public static final Pattern EXTERNAL_API_PATH =
			Pattern.compile("^/api/(v\\d+)/(agent|iot)(?:/.*)?$");

	private GatewayApiKeyAuthFilterConstants() {
	}
}
