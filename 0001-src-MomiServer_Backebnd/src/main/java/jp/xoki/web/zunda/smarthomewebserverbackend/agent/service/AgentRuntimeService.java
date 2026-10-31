package jp.xoki.web.zunda.smarthomewebserverbackend.agent.service;

import java.util.List;
import java.util.UUID;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.model.AgentPrincipal;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.repository.AgentRuntimeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Agent同期のDB操作と、実行所有者確認を含むトランザクションを管理する。 */
@Service
@RequiredArgsConstructor
public class AgentRuntimeService {
    private final AgentRuntimeRepository repository;
    private final AgentAuthorization authorization;

    public String operatingSystem(UUID agentId) {
        return repository.operatingSystem(agentId);
    }

    public List<UUID> executionIds(AgentPrincipal principal) {
        return repository.executionIds(principal);
    }

    public void heartbeat(AgentPrincipal principal, boolean mqttConnected) {
        repository.heartbeat(principal, mqttConnected);
    }

    @Transactional
    public void unconfirmed(UUID executionId, String reason) {
        // 所有者確認のロックを、確認状態の更新が完了するまで維持する。
        authorization.requireExecution(executionId);
        repository.unconfirmed(executionId, reason);
    }
}
