package jp.xoki.web.zunda.smarthomewebserverbackend.scheduling.scheduler;

import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.ApplicationTimeConstants.ZONE_ID;

import jp.xoki.web.zunda.smarthomewebserverbackend.batch.service.BatchExecutionRequestService;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.service.BatchSchedulePlanningService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

/** バッチ実行制御の定期実行入口。登録はSchedulingConfigの有効化条件に従う。 */
@RequiredArgsConstructor
public class BatchExecutionScheduler {

	private final BatchSchedulePlanningService planning;
	private final BatchExecutionRequestService requests;

	/** 当日・翌日の実行要求作成。
	 *
	 * 毎日00:00と起動完了時に、業務Serviceで未作成分を補完する。
	 */
	@Scheduled(cron = "0 0 0 * * *", zone = ZONE_ID)
	@EventListener(ApplicationReadyEvent.class)
	public void enqueueScheduledBatches() {
		planning.enqueueScheduledBatches();
	}

	/** 実行要求の定期配信。
	 *
	 * 既定で30秒ごとに、配信対象の判定とAgentへの通知をServiceへ委譲する。
	 */
	@Scheduled(fixedDelayString = "${batch.execution.dispatch-retry-delay-ms:30000}")
	public void redispatchQueuedRequests() {
		requests.redispatchQueuedRequests();
	}

	/** 実行要求の期限監視。
	 *
	 * 既定で30秒ごとに、開始・結果・停止確認の期限判定をServiceへ委譲する。
	 */
	@Scheduled(fixedDelayString = "${batch.execution.timeout-scan-delay-ms:30000}")
	public void monitorExecutionRequests() {
		requests.monitorRequests();
	}

	/** キャンセル要求の再配信。
	 *
	 * 既定で30秒ごとに、再通知が必要な要求の処理をServiceへ委譲する。
	 */
	@Scheduled(fixedDelayString = "${batch.execution.cancel-retry-delay-ms:30000}")
	public void redispatchCancellations() {
		requests.redispatchCancellations();
	}
}
