package jp.xoki.web.zunda.smarthomewebserverbackend.scheduling.config;

import jp.xoki.web.zunda.smarthomewebserverbackend.batch.service.BatchExecutionRequestService;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.service.BatchSchedulePlanningService;
import jp.xoki.web.zunda.smarthomewebserverbackend.scheduling.scheduler.BatchExecutionScheduler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** 全機能の定期実行基盤と起動時バッチの有効化設定。
 *
 * 通常は有効とし、CIの一時検証環境では明示的に停止できるようにする。
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "app.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {

	/** バッチ実行制御の登録。
	 *
	 * 定期処理を停止した場合は、ApplicationReadyEventによる起動時展開も登録しない。
	 *
	 * @param planning 実行予定の展開Service
	 * @param requests バッチ実行要求Service
	 * @return バッチ実行制御
	 */
	@Bean
	public BatchExecutionScheduler batchExecutionScheduler(BatchSchedulePlanningService planning,
			BatchExecutionRequestService requests) {
		return new BatchExecutionScheduler(planning, requests);
	}

	/** 共通の定期実行スレッドプール。
	 *
	 * 移行前の並列数・スレッド名・終了待機を維持する。
	 *
	 * @return 全Schedulerが共用する実行基盤
	 */
	@Bean
	public ThreadPoolTaskScheduler taskScheduler() {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(4);
		scheduler.setThreadNamePrefix("batch-control-");
		scheduler.setWaitForTasksToCompleteOnShutdown(true);
		scheduler.setAwaitTerminationSeconds(30);
		return scheduler;
	}
}
