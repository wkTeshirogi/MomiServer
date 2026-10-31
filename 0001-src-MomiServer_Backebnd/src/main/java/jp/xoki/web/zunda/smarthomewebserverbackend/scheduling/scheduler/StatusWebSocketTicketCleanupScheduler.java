package jp.xoki.web.zunda.smarthomewebserverbackend.scheduling.scheduler;

import jp.xoki.web.zunda.smarthomewebserverbackend.status.service.StatusWebSocketTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** WebSocket接続チケットの定期整理入口。 */
@Component
@RequiredArgsConstructor
public class StatusWebSocketTicketCleanupScheduler {

	private final StatusWebSocketTicketService service;

	/** 期限切れチケットの整理。
	 *
	 * 起動1分後から1分間隔で、同一Serviceが保持するチケットを整理する。
	 */
	@Scheduled(fixedDelay = 60000, initialDelay = 60000)
	public void cleanup() {
		service.cleanup();
	}
}
