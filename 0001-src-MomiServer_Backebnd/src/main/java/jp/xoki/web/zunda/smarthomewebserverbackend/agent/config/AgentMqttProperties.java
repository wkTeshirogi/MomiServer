package jp.xoki.web.zunda.smarthomewebserverbackend.agent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Dedicated Mosquitto control connection; never returned to agents. */
@Getter
@Setter
@Component
@ConfigurationProperties("app.agent-mqtt")
public class AgentMqttProperties {
    private boolean enabled;
    private String brokerUrl = "ssl://localhost:8883";
    private String agentBrokerUrl = "ssl://localhost:8883";
    private String username = "";
    private String password = "";
    private String clientId = "momiserver-dynsec";
    private boolean allowInsecureLocalhost;
}
