package jp.xoki.web.zunda.smarthomewebserverbackend.common.constants;

import java.util.regex.Pattern;

/** ApiAccessLogFilterで使用する固定値。カテゴリ内の利用元から参照する。 */
public final class ApiAccessLogFilterConstants {

	public static final int MAX_PATH_LENGTH = 4096;

	public static final int MAX_ENDPOINT_LENGTH = 1024;

	public static final int MAX_PRINCIPAL_LENGTH = 255;

	public static final int MAX_IP_LENGTH = 45;

	public static final int MAX_USER_AGENT_LENGTH = 512;

	public static final int MAX_ERROR_CODE_LENGTH = 100;

	public static final String SOURCE_TYPE_AGENT = "agent";

	public static final Pattern VERSIONED_SOURCE_PATH = Pattern.compile("/api/v\\d++/(external|agent|iot)(?=/|$)");

	private ApiAccessLogFilterConstants() {
	}
}
