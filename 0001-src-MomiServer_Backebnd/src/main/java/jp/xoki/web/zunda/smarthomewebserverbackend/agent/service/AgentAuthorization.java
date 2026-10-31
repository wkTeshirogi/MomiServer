package jp.xoki.web.zunda.smarthomewebserverbackend.agent.service;

import org.springframework.http.HttpStatus;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.model.AgentPrincipal;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.repository.AgentAuthRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants.AgentMessageConstants;

/** Agent自身の端末・登録世代へ割り当てられた実行のみ許可する。 */
@Service
@RequiredArgsConstructor
public class AgentAuthorization {
    private final AgentAuthRepository repository;

    public void requireExecution(UUID executionId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AgentPrincipal principal)) {
            throw AgentAuthException.unauthorized();
        }
        if (!repository.lockExecutionOwner(principal, executionId)) {
            throw new AgentAuthException(HttpStatus.NOT_FOUND.value(), "AGENT_EXECUTION_NOT_FOUND", AgentMessageConstants.EXECUTION_NOT_FOUND);
        }
    }
}
