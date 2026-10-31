package jp.xoki.web.zunda.smarthomewebserverbackend.agent.model;

import java.security.Principal;
import java.util.UUID;

/**
 * 管理ユーザーとは独立した端末認証主体。
 * @param agentId Agentの外部UUID
 * @param deviceId 端末マスタID
 * @param generation 登録世代
 */
public record AgentPrincipal(UUID agentId, long deviceId, long generation) implements Principal {
    @Override
    public String getName() {
        return agentId.toString();
    }
}
