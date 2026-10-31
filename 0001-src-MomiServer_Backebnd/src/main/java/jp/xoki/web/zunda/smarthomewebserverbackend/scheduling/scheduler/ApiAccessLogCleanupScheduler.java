package jp.xoki.web.zunda.smarthomewebserverbackend.scheduling.scheduler;

import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.ApplicationTimeConstants.ZONE_ID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.Clock;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.service.ApiAccessLogService;

/** APIアクセスログの保持期間を適用する定期処理。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApiAccessLogCleanupScheduler {

	private final ApiAccessLogService service;

	private final Clock clock;

	/** バッチ処理: 期限切れAPIアクセスログ削除。
	 *
	 * 毎日02:15に保持期間を過ぎたAPIアクセスログを削除する。
	 */
	@Scheduled(cron = "${app.api-access-log.cleanup-cron:0 15 2 * * *}", zone = ZONE_ID)
	public void cleanupExpired() {
		int deleted = service.cleanupExpired(OffsetDateTime.now(clock));
		log.info("Expired API access logs deleted. count={}", deleted);
	}
}
