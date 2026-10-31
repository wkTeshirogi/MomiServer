package jp.xoki.web.zunda.smarthomewebserverbackend.common.constants;

import java.util.Set;

/** ApiAccessLogServiceで使用する固定値。カテゴリ内の利用元から参照する。 */
public final class ApiAccessLogServiceConstants {

	public static final int MAX_LIMIT = 200;

	public static final long MAX_RANGE_DAYS = 31;

	public static final Set<String> METHODS =
			Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD");

	public static final Set<String> STATUS_CATEGORIES =
			Set.of("success", "client_error", "server_error");

	public static final Set<String> SOURCE_TYPES =
			Set.of("web", "agent", "iot", "gateway", "anonymous");

	private ApiAccessLogServiceConstants() {
	}
}
