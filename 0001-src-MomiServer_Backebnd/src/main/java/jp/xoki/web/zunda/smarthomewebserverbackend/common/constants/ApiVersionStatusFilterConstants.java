package jp.xoki.web.zunda.smarthomewebserverbackend.common.constants;

import java.util.regex.Pattern;

/** ApiVersionStatusFilterで使用する固定値。カテゴリ内の利用元から参照する。 */
public final class ApiVersionStatusFilterConstants {

	public static final Pattern EXTERNAL_API_PATH =
			Pattern.compile("^/api/(v\\d+)/(agent|iot|external)(?:/.*)?$");

	public static final String SUPPORTED_VERSION = "v1";

	private ApiVersionStatusFilterConstants() {
	}
}
