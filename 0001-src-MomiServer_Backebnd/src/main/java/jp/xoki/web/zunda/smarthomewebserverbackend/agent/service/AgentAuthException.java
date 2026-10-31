package jp.xoki.web.zunda.smarthomewebserverbackend.agent.service;

import org.springframework.http.HttpStatus;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants.AgentMessageConstants;

/** 機密情報を含まないAgent APIエラー。 */
public class AgentAuthException extends RuntimeException {
    private final int status;
    private final String code;

    public AgentAuthException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() {
        return status;
    }
    public String code() {
        return code;
    }

    public static AgentAuthException unauthorized() {
        return new AgentAuthException(HttpStatus.UNAUTHORIZED.value(), "AGENT_AUTHENTICATION_FAILED",
                AgentMessageConstants.AUTHENTICATION_FAILED);
    }
}
