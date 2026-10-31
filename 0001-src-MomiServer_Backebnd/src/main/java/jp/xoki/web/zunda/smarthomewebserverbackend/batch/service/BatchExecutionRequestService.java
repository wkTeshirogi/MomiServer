package jp.xoki.web.zunda.smarthomewebserverbackend.batch.service;

import jp.xoki.web.zunda.jooq.tables.records.MBatchDefinitionsRecord;
import jp.xoki.web.zunda.jooq.tables.records.MBatchSchedulesRecord;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.AgentBatchCompleteRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchExecutionRequestCreateRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchExecutionRequestDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.model.BatchExecutionStatus;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository.BatchDefinitionRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository.BatchExecutionRequestRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.ResourceNotFoundException;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.service.BatchGlobalSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.time.Clock;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.BatchGlobalSettingDetailDto;

/** バッチ実行要求の管理。
 *
 * 手動・スケジュール実行要求を作成し、Agentへの配信や実行状態の更新を制御サービスへ委譲する。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BatchExecutionRequestService {

	/** バッチ定義Repository。 */
	private final BatchDefinitionRepository batchDefinitionRepository;

	/** バッチ実行要求Repository。 */
	private final BatchExecutionRequestRepository batchExecutionRequestRepository;

	private final BatchGlobalSettingService batchGlobalSettingService;
	private final BatchExecutionControlService controlService;

	private final Clock clock;

	/** 更新前に実行中・開始未確認として残っている要求数を取得する。 */
	public long countUnsettledExecutions() {
		return batchExecutionRequestRepository.countUnsettledExecutions();
	}

	/** 手動実行要求の作成。
	 *
	 * バッチ定義を検証し、共通設定を反映した実行要求をDBへ保存する。Agentへの配信は定期処理が行う。
	 *
	 * @param batchDefinitionId バッチ定義ID
	 * @param request 実行要求内容
	 * @param requestedBy 実行要求者
	 * @return 作成した実行要求
	 */
	@Transactional
	public BatchExecutionRequestDetailDto createManualRequest(
			Long batchDefinitionId,
			BatchExecutionRequestCreateRequest request,
			String requestedBy
	) {
		MBatchDefinitionsRecord definition = batchDefinitionRepository.lockById(batchDefinitionId)
				.orElseThrow(() -> new ResourceNotFoundException("batch definition", batchDefinitionId));
		validateExecutableDefinition(definition);

		BatchExecutionRequestDetailDto executionRequest = buildRequest(
				definition,
				new RequestSource(null, request.getTargetAgentId(), "manual", requestedBy,
						Boolean.TRUE.equals(request.getTestExecution())),
				null, LocalDateTime.now(clock));

		Long id = batchExecutionRequestRepository.create(executionRequest);
		return batchExecutionRequestRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("batch execution request", id));
	}

	/** スケジュール実行要求の作成。
	 *
	 * バッチ定義を検証し、スケジュールIDと発火日時を持つ実行要求をDBへ保存する。
	 * このメソッドではCron式の判定やAgentへの配信は行わない。
	 *
	 * @param schedule バッチスケジュール
	 * @param definition バッチ定義
	 * @param scheduledFireTime スケジュール発火日時
	 * @return 作成した実行要求。重複要求の場合はnull
	 */
	@Transactional
	public BatchExecutionRequestDetailDto createScheduledRequest(
			MBatchSchedulesRecord schedule,
			MBatchDefinitionsRecord definition,
			LocalDateTime scheduledFireTime
	) {
		return createScheduledRequestInternal(schedule, definition, scheduledFireTime,
				LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS));
	}

	/** 基準日時を指定したスケジュール実行要求の作成。
	 *
	 * 当日・翌日の各予定を保存する。基準日時より前の未作成分はexpiredで記録し、配信しない。
	 * 既存要求は一意制約により保護し、状態や設定値を変更しない。
	 *
	 * @param schedule バッチスケジュール
	 * @param definition バッチ定義
	 * @param scheduledFireTime 実行予定日時
	 * @param planningTime 作成処理の基準日時
	 * @return 作成した実行要求。重複要求の場合はnull
	 */
	@Transactional
	public BatchExecutionRequestDetailDto createScheduledRequest(
			MBatchSchedulesRecord schedule, MBatchDefinitionsRecord definition,
			LocalDateTime scheduledFireTime, LocalDateTime planningTime
	) {
		return createScheduledRequestInternal(schedule, definition, scheduledFireTime, planningTime);
	}

	private BatchExecutionRequestDetailDto createScheduledRequestInternal(
			MBatchSchedulesRecord schedule, MBatchDefinitionsRecord definition,
			LocalDateTime scheduledFireTime, LocalDateTime planningTime
	) {
		Long definitionId = definition.getId();

		// スケジューラが読んだ後に定義が無効化される場合があるため、登録直前に行ロックして再検証する。
		definition = batchDefinitionRepository.lockById(definitionId)
				.orElseThrow(() -> new ResourceNotFoundException("batch definition", definitionId));
		validateExecutableDefinition(definition);
		BatchExecutionRequestDetailDto executionRequest = buildRequest(
				definition,
				new RequestSource(schedule.getId(), schedule.getTargetAgentId(), "schedule", "scheduler", false),
				scheduledFireTime, planningTime);


		// 同じ予定の同時展開はRepositoryの一意制約で吸収する。既存要求の状態や受付時設定は上書きしない。
		Long id = batchExecutionRequestRepository.create(executionRequest);
		if (id == null) return null;
		return batchExecutionRequestRepository.findById(id).orElseThrow();
	}

	/** 実行要求詳細の取得。
	 *
	 * 外部参照用実行IDで実行要求を検索し、該当するデータがない場合は例外を送出する。
	 *
	 * @param executionId 外部参照用実行ID
	 * @return 実行要求詳細
	 * @throws ResourceNotFoundException 実行要求が存在しない場合
	 */
	public BatchExecutionRequestDetailDto getDetail(UUID executionId) {
		return batchExecutionRequestRepository.findByExecutionId(executionId)
				.orElseThrow(() -> new ResourceNotFoundException("batch execution request", executionId));
	}

	/** 開始日時未指定の実行開始通知。
	 *
	 * Agentからの開始通知を制御サービスへ渡し、サーバーの受信日時を開始日時として扱う。
	 *
	 * @param executionId 外部参照用実行ID
	 * @return 更新後の実行要求詳細
	 */
	public BatchExecutionRequestDetailDto markRunning(UUID executionId) {
		return controlService.start(executionId, null);
	}

	/** 開始日時付きの実行開始通知。
	 *
	 * Agentが報告した開始日時を制御サービスへ渡し、実行状態と結果待ち期限を更新する。
	 *
	 * @param executionId 外部参照用実行ID
	 * @param startedAt Agentでの開始日時。nullの場合はサーバーの受信日時を使用する
	 * @return 更新後の実行要求詳細
	 */
	public BatchExecutionRequestDetailDto markRunning(UUID executionId, LocalDateTime startedAt) {
		return controlService.start(executionId, startedAt);
	}

	/** 実行完了通知の反映。
	 *
	 * Agentからの完了通知を制御サービスへ渡し、状態遷移の検証、実行要求の更新、履歴登録を行う。
	 *
	 * @param executionId 外部参照用実行ID
	 * @param request Agentからの完了通知
	 * @return 完了通知反映後の実行要求詳細
	 */
	public BatchExecutionRequestDetailDto complete(UUID executionId, AgentBatchCompleteRequest request) {
		return controlService.complete(executionId, request);
	}

	/** 実行要求のキャンセル受付。
	 *
	 * キャンセル要求を記録して、必要な場合にAgentへの停止通知を試行する。停止確認前に完了扱いにはしない。
	 *
	 * @param executionId 外部参照用実行ID
	 * @param requestedBy キャンセル要求者
	 * @return キャンセル受付後の実行要求詳細
	 */
	public BatchExecutionRequestDetailDto cancel(UUID executionId, String requestedBy) {
		controlService.requestCancellation(executionId, requestedBy);
		controlService.dispatchCancellation(executionId, LocalDateTime.now(clock));
		return getDetail(executionId);
	}

	/** 配信待ち実行要求の通知。
	 *
	 * 配信対象を最大50件取得し、実行要求ごとにAgentへの通知を試行する。
	 */
	public void redispatchQueuedRequests() {
		LocalDateTime now = LocalDateTime.now(clock);
		batchExecutionRequestRepository.findPendingDispatch(now, 50).forEach(request ->
				runSafely(request.getExecutionId(), () -> controlService.dispatch(request.getExecutionId(), LocalDateTime.now(clock))));
	}

	/** 実行要求の状態監視。
	 *
	 * 期限監視の対象を最大100件取得し、実行要求ごとに状態確認と必要な通知を行う。
	 */
	public void monitorRequests() {
		LocalDateTime now = LocalDateTime.now(clock);
		batchExecutionRequestRepository.findMonitorCandidates(now, 100).forEach(request ->
				runSafely(request.getExecutionId(), () -> controlService.monitor(request.getExecutionId(), LocalDateTime.now(clock))));
	}

	/** キャンセル要求の再通知。
	 *
	 * 再通知対象を最大50件取得し、実行要求ごとにAgentへのキャンセル通知を試行する。
	 */
	public void redispatchCancellations() {
		LocalDateTime now = LocalDateTime.now(clock);
		batchExecutionRequestRepository.findPendingCancellation(now, 50).forEach(request ->
				runSafely(request.getExecutionId(), () -> controlService.dispatchCancellation(request.getExecutionId(), LocalDateTime.now(clock))));
	}

	/** 実行要求単位の例外処理。
	 *
	 * 処理中の実行時例外を警告ログへ記録し、他の実行要求の処理を継続できるようにする。
	 *
	 * @param executionId ログに記録する外部参照用実行ID
	 * @param action 実行する制御処理
	 */
	private void runSafely(UUID executionId, Runnable action) {
		try {
			action.run();
		} catch (RuntimeException exception) {
			log.warn("Batch control failed. executionId={}", executionId, exception);
		}
	}

	/** 保存用実行要求の組み立て。
	 *
	 * バッチ定義と共通設定を実行要求へ反映し、受付時点の設定値と開始待ち期限を保持する。
	 *
	 * @param definition バッチ定義
	 * @param source 実行要求の発生元と実行先
	 * @param scheduledFireTime スケジュール発火日時
	 * @param now 作成処理の基準日時
	 * @return DB保存前の実行要求DTO
	 * @throws IllegalArgumentException 結果待ち期限が実行タイムアウト以下の場合
	 */
	private BatchExecutionRequestDetailDto buildRequest(
			MBatchDefinitionsRecord definition,
			RequestSource source,
			LocalDateTime scheduledFireTime,
			LocalDateTime now
	) {
		BatchGlobalSettingDetailDto settings = batchGlobalSettingService.detail();
		int timeout = definition.getTimeoutSeconds() != null && definition.getTimeoutSeconds() > 0
				? definition.getTimeoutSeconds() : settings.getDefaultTimeoutSeconds();
		if (settings.getResultWaitTimeoutSeconds() <= timeout) {
			throw new IllegalArgumentException("Result wait timeout must exceed the batch execution timeout");
		}
		boolean missed = scheduledFireTime != null && scheduledFireTime.isBefore(now);
		LocalDateTime startBase = scheduledFireTime != null ? scheduledFireTime : now;
		return BatchExecutionRequestDetailDto.builder()
				.batchDefinitionId(definition.getId())
				.scheduleId(source.scheduleId())
				.targetAgentId(source.targetAgentId())
				.requestedType(source.requestedType())
				.requestedBy(source.requestedBy())
				.executionStatus(missed ? BatchExecutionStatus.EXPIRED : BatchExecutionStatus.QUEUED)
				.testExecution(source.testExecution())
				.executionCommand(definition.getExecutionCommand())
				.workingDirectory(definition.getWorkingDirectory())
				.timeoutSeconds(timeout)
				.settingVersion(settings.getSettingVersion())
				.maxConcurrentExecutions(settings.getMaxConcurrentExecutions())
				.resultRetryIntervalSeconds(settings.getResultRetryIntervalSeconds())
				.resultRetryCount(settings.getResultRetryCount())
				.unsentResultRetentionDays(settings.getUnsentResultRetentionDays())
				.resultWaitTimeoutSeconds(settings.getResultWaitTimeoutSeconds().longValue())
				.cancelGraceSeconds(settings.getCancelGraceSeconds())
				.requestedAt(now)
				.startDeadlineAt(startBase.plusSeconds(settings.getStartWaitTimeoutSeconds()))
				.confirmationStatus(missed ? "confirmed" : "pending")
				.finishedAt(missed ? now : null)
				.message(missed ? "Scheduled time passed before request creation; execution skipped" : null)
				.maxRetryCount(definition.getMaxRetryCount())
				.retryCount(0)
				.dispatchAttemptCount(0)
				.logPath(definition.getLogPath())
				.scheduledFireTime(scheduledFireTime)
				.build();
	}

	private record RequestSource(Long scheduleId, Long targetAgentId, String requestedType,
			String requestedBy, boolean testExecution) { }

	/** バッチ定義の実行可否検証。
	 *
	 * 定義が無効化されている場合、または実行コマンドが未設定の場合に実行を拒否する。
	 *
	 * @param definition バッチ定義
	 * @throws IllegalArgumentException 定義が無効または実行コマンドが未設定の場合
	 */
	private void validateExecutableDefinition(MBatchDefinitionsRecord definition) {

		// 無効化されたバッチ定義は手動実行・スケジュール実行のどちらも拒否する。
		if (Boolean.FALSE.equals(definition.getEnabled()) || "deleted".equals(definition.getStatus())) {
			throw new IllegalArgumentException("Disabled batch definition cannot be executed");
		}

		// 実行コマンドが未設定の場合はAgentに送信できないため拒否する。
		if (definition.getExecutionCommand() == null || definition.getExecutionCommand().isBlank()) {
			throw new IllegalArgumentException("Execution command is not configured");
		}
	}

}
