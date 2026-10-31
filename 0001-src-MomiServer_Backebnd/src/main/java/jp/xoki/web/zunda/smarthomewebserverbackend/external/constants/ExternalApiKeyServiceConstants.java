package jp.xoki.web.zunda.smarthomewebserverbackend.external.constants;

import java.util.Set;

/** ExternalApiKeyServiceで使用する固定値。カテゴリ内の利用元から参照する。 */
public final class ExternalApiKeyServiceConstants {

	public static final int PREFIX_LENGTH = 12;

	public static final Set<String> SCOPES = Set.of("inventory:read", "inventory:write");

	private ExternalApiKeyServiceConstants() {
	}
}
