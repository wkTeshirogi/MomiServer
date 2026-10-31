package jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants;

import java.util.Set;

/** AgentAuthFilterで使用する固定値。カテゴリ内の利用元から参照する。 */
public final class AgentAuthFilterConstants {

    public static final int MAX_AUTHORIZATION_LENGTH = 110;

    public static final String BEARER_PREFIX = "Bearer ";

    public static final Set<String> BOOTSTRAP_PATHS = Set.of("/api/v1/agent/register",
            "/api/v1/agent/auth/challenge", "/api/v1/agent/auth/refresh");

	private AgentAuthFilterConstants() {
	}
}
