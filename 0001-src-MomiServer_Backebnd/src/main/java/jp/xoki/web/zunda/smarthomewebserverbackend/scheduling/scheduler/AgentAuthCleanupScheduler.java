package jp.xoki.web.zunda.smarthomewebserverbackend.scheduling.scheduler;

import jp.xoki.web.zunda.smarthomewebserverbackend.agent.service.AgentAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Agent認証関連データの定期整理入口。 */
@Component
@RequiredArgsConstructor
public class AgentAuthCleanupScheduler {

	private final AgentAuthService service;

	/** 認証関連データの整理。
	 *
	 * 起動1時間後から1時間間隔で、削除対象の判定をServiceへ委譲する。
	 */
	@Scheduled(fixedDelay = 3600000, initialDelay = 3600000)
	public void cleanup() {
		service.cleanup();
	}
}
