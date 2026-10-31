package jp.xoki.web.zunda.smarthomewebserverbackend.agent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import static jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants.AgentAuthPropertiesConstants.DEFAULT_ACCESS_MINUTES;
import static jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants.AgentAuthPropertiesConstants.DEFAULT_RECOVERY_HOURS;
import static jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants.AgentAuthPropertiesConstants.DEFAULT_REFRESH_DAYS;
import static jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants.AgentAuthPropertiesConstants.DEFAULT_REFRESH_RECOVERY_MINUTES;
import static jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants.AgentAuthPropertiesConstants.DEFAULT_REGISTRATION_MINUTES;
import static jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants.AgentAuthPropertiesConstants.DEFAULT_REQUESTS_PER_MINUTE;


/** Agent認証の期限と、再送応答の暗号化に使用する専用鍵。 */
@Component
@ConfigurationProperties("app.agent-auth")
@Getter
@Setter
public class AgentAuthProperties {
    private String responseEncryptionKey = "";
    private int accessMinutes = DEFAULT_ACCESS_MINUTES;
    private int refreshDays = DEFAULT_REFRESH_DAYS;
    private int registrationMinutes = DEFAULT_REGISTRATION_MINUTES;
    private int recoveryHours = DEFAULT_RECOVERY_HOURS;
    private int refreshRecoveryMinutes = DEFAULT_REFRESH_RECOVERY_MINUTES;
    private int requestsPerMinute = DEFAULT_REQUESTS_PER_MINUTE;
}
