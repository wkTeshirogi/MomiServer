package jp.xoki.web.zunda.smarthomewebserverbackend.scheduling.scheduler;

import jp.xoki.web.zunda.smarthomewebserverbackend.agent.service.AgentMqttProvisioning;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** AgentのMQTT資格情報・権限同期の定期実行入口。 */
@Component
@RequiredArgsConstructor
public class AgentMqttProvisioningScheduler {

	private final AgentMqttProvisioning service;

	/** MQTT設定の同期。
	 *
	 * 起動5秒後から5秒間隔で委譲し、接続可否・再試行の判定はService側に維持する。
	 */
	@Scheduled(fixedDelay = 5000, initialDelay = 5000)
	public void reconcile() {
		service.reconcile();
	}
}
