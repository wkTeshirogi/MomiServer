package jp.xoki.web.zunda.smarthomewebserverbackend.batch.service;

import jp.xoki.web.zunda.jooq.tables.records.TBatchExecutionHistoriesRecord;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.AgentBatchCompleteRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchExecutionRequestDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.model.BatchExecutionStatus;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository.BatchExecutionHistoryRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository.BatchExecutionRequestRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.BrowserNotificationCreateDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.ResourceNotFoundException;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationLevel;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationSourceType;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationType;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.util.Notifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.time.Clock;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.constants.BatchMessageConstants;

/** Serializes transitions per execution; network publication follows the committed dispatch intent. */
@Service
@RequiredArgsConstructor
@Slf4j
public class BatchExecutionControlService {

	private final BatchExecutionRequestRepository repository;
	private final BatchExecutionHistoryRepository historyRepository;
	private final BatchCommandPublishService publisher;
	private final Notifications notifications;
	private final PlatformTransactionManager transactionManager;

	private final Clock clock;
	private final BatchMaintenanceService maintenance;

	private record MonitorDecision(String confirmation, String title, boolean expired) { }

	public BatchExecutionRequestDetailDto start(UUID executionId, LocalDateTime startedAt) {
		return maintenance.withStartGate(() -> resultTransaction(() -> startInTransaction(executionId, startedAt)));
	}

	/** 行ロックと保守ゲートを保持した呼出元のトランザクション内で開始を判定する。 */
	private BatchExecutionRequestDetailDto startInTransaction(UUID executionId, LocalDateTime startedAt) {
		BatchExecutionRequestDetailDto current = locked(executionId);
		if (current.getExecutionStatus().isTerminal()
				|| current.getExecutionStatus() == BatchExecutionStatus.RUNNING) return current;

		// 保守拒否は未開始状態を明示して返す。Agentはプロセスを起動せず、解除後に再照合する。
		if (maintenance.isPaused()) return current;
		LocalDateTime now = LocalDateTime.now(clock);
		validateStart(current, startedAt, now);
		BatchExecutionRequestDetailDto updated = current.toBuilder().executionStatus(BatchExecutionStatus.RUNNING)
				.startedAt(startedAt != null ? startedAt : now).startReceivedAt(now)
				.resultDeadlineAt(now.plusSeconds(current.getResultWaitTimeoutSeconds()))
				.confirmationStatus(current.getCancelRequestedAt() == null ? "pending" : current.getConfirmationStatus())
				.build();
		repository.saveControlState(updated);
		return updated;
	}

	private void validateStart(BatchExecutionRequestDetailDto current, LocalDateTime startedAt, LocalDateTime now) {
		if (startedAt == null && (current.getCancelRequestedAt() != null || !now.isBefore(current.getStartDeadlineAt()))) {
			throw new IllegalArgumentException("Execution cannot start after cancellation or its deadline");
		}
		if (startedAt != null && startedAt.isAfter(current.getStartDeadlineAt())) {
			throw new IllegalArgumentException("Execution started after its start deadline");
		}
	}

	public BatchExecutionRequestDetailDto complete(UUID executionId, AgentBatchCompleteRequest result) {
		return resultTransaction(() -> completeInTransaction(executionId, result));
	}

	private BatchExecutionRequestDetailDto completeInTransaction(UUID executionId, AgentBatchCompleteRequest result) {
		BatchExecutionRequestDetailDto current = locked(executionId);
		BatchExecutionStatus status = terminalStatus(result);
		BatchExecutionRequestDetailDto alreadyCompleted = alreadyCompleted(current, status);
		if (alreadyCompleted != null) {
			return alreadyCompleted;
		}
		validateCompletion(current, result, status);
		repository.saveOutput(executionId, result.getStdout(), result.getStderr(),
				Boolean.TRUE.equals(result.getOutputTruncated()));
		return finish(current.toBuilder().exitCode(result.getExitCode())
				.logPath(result.getLogPath() != null ? result.getLogPath() : current.getLogPath())
				.message(result.getMessage()).build(), status, LocalDateTime.now(clock));
	}

	private BatchExecutionStatus terminalStatus(AgentBatchCompleteRequest result) {
		BatchExecutionStatus status = result.getExecutionStatus();
		if (status == null || !status.isTerminal()) {
			throw new IllegalArgumentException("Execution status is not terminal");
		}
		return status;
	}

	private BatchExecutionRequestDetailDto alreadyCompleted(
			BatchExecutionRequestDetailDto current, BatchExecutionStatus status) {
		if (!current.getExecutionStatus().isTerminal()) {
			return null;
		}
		if (current.getExecutionStatus() != status) {
			throw new IllegalArgumentException("A different terminal result has already been accepted");
		}
		return current;
	}

	private void validateCompletion(BatchExecutionRequestDetailDto current,
			AgentBatchCompleteRequest result, BatchExecutionStatus status) {
		validateTimeout(result, status);
		validateCancellation(current, result, status);
		validateExpiry(current, result, status);
	}

	private void validateTimeout(AgentBatchCompleteRequest result, BatchExecutionStatus status) {

		// 通信途絶とプロセス停止は別なので、期限経過だけでTIMEOUTとして確定させない。
		if (status == BatchExecutionStatus.TIMEOUT && !Boolean.TRUE.equals(result.getStopConfirmed())) {
			throw new IllegalArgumentException("Timeout requires confirmed process termination");
		}
	}

	private void validateCancellation(BatchExecutionRequestDetailDto current,
			AgentBatchCompleteRequest result, BatchExecutionStatus status) {
		if (status == BatchExecutionStatus.CANCELLED
				&& (current.getCancelRequestedAt() == null
				|| !(Boolean.TRUE.equals(result.getStopConfirmed())
				|| (Boolean.TRUE.equals(result.getNotStarted()) && current.getStartedAt() == null)))) {
			throw new IllegalArgumentException("Cancellation requires a request and confirmed stop or non-start");
		}
	}

	private void validateExpiry(BatchExecutionRequestDetailDto current,
			AgentBatchCompleteRequest result, BatchExecutionStatus status) {
		if (status == BatchExecutionStatus.EXPIRED && (current.getStartedAt() != null
				|| !Boolean.TRUE.equals(result.getNotStarted())
				|| LocalDateTime.now(clock).isBefore(current.getStartDeadlineAt()))) {
			throw new IllegalArgumentException("Expiry requires confirmed non-start after the deadline");
		}
	}

	public BatchExecutionRequestDetailDto requestCancellation(UUID executionId, String requestedBy) {
		return requestCancellation(executionId, requestedBy, false);
	}

	/**
	 * 定義無効化に伴い、未開始の場合だけ取消を要求する。
	 *
	 * @param executionId 外部参照用実行ID
	 * @param requestedBy 取消要求者
	 * @return 取消受付後、または実行中のため変更しなかった実行要求
	 */
	public BatchExecutionRequestDetailDto requestCancellationIfNotStarted(UUID executionId, String requestedBy) {
		return requestCancellation(executionId, requestedBy, true);
	}

	private BatchExecutionRequestDetailDto requestCancellation(
			UUID executionId, String requestedBy, boolean skipRunning) {
		return transaction(() -> {
			BatchExecutionRequestDetailDto current = locked(executionId);
			if (current.getExecutionStatus().isTerminal() || current.getCancelRequestedAt() != null) return current;
			if (skipRunning && current.getExecutionStatus() == BatchExecutionStatus.RUNNING) return current;
			BatchExecutionRequestDetailDto updated = current.toBuilder().cancelRequestedAt(LocalDateTime.now(clock))
					.cancelRequestedBy(requestedBy).build();
			if (neverDispatched(current)) return finish(updated, BatchExecutionStatus.CANCELLED, LocalDateTime.now(clock));
			repository.saveControlState(updated);
			return updated;
		});
	}

	/** 実行要求の配信。
	 *
	 * 行ロック下で予定時刻と開始待ち期限を確認し、配信試行をコミットしてからMQTTへ送信する。
	 *
	 * @param executionId 外部参照用実行ID
	 * @param now 配信可否の判定日時
	 */
	public void dispatch(UUID executionId, LocalDateTime now) {
		maintenance.withStartGate(() -> {
			if (!maintenance.isPaused()) dispatchWhileActive(executionId, now);
			return null;
		});
	}

	private void dispatchWhileActive(UUID executionId, LocalDateTime now) {
		if (!publisher.isEnabled()) return;

		// 送信試行を先に確定し、ネットワーク待ちの間はDBロックを保持しない。送信例外だけでは実行失敗と断定しない。
		BatchExecutionRequestDetailDto claimed = transaction(() -> {
			BatchExecutionRequestDetailDto current = locked(executionId);
			if (current.getExecutionStatus().isTerminal()
					|| current.getExecutionStatus() == BatchExecutionStatus.RUNNING
					|| current.getCancelRequestedAt() != null
					|| (current.getScheduledFireTime() != null && now.isBefore(current.getScheduledFireTime()))
					|| !now.isBefore(current.getStartDeadlineAt())
					|| recentlyAttempted(current.getLastDispatchAttemptAt(), now)) return null;
			BatchExecutionRequestDetailDto updated = current.toBuilder().executionStatus(BatchExecutionStatus.DISPATCHED)
					.dispatchedAt(current.getDispatchedAt() != null ? current.getDispatchedAt() : now)
					.lastDispatchAttemptAt(now)
					.dispatchAttemptCount(current.getDispatchAttemptCount() + 1).build();
			repository.saveControlState(updated);
			return updated;
		});
		if (claimed != null && LocalDateTime.now(clock).isBefore(claimed.getStartDeadlineAt())) {
			try {
				publisher.publish(claimed);
			} catch (RuntimeException exception) {
				log.warn("Batch dispatch not confirmed. executionId={}", executionId, exception);
			}
		}
	}

	public void dispatchCancellation(UUID executionId, LocalDateTime now) {
		if (!publisher.isEnabled()) return;
		BatchExecutionRequestDetailDto claimed = transaction(() -> {
			BatchExecutionRequestDetailDto current = locked(executionId);
			if (current.getExecutionStatus().isTerminal() || current.getCancelRequestedAt() == null
					|| recentlyAttempted(current.getLastCancelAttemptAt(), now)) return null;
			BatchExecutionRequestDetailDto updated = current.toBuilder().lastCancelAttemptAt(now).build();
			repository.saveControlState(updated);
			return updated;
		});
		if (claimed != null) {
			try {
				publisher.publishCancellation(claimed);
			} catch (RuntimeException exception) {
				log.warn("Batch cancellation not confirmed. executionId={}", executionId, exception);
			}
		}
	}

	public void monitor(UUID executionId, LocalDateTime now) {
		transaction(() -> monitorInTransaction(executionId, now));
	}

	private BatchExecutionRequestDetailDto monitorInTransaction(UUID executionId, LocalDateTime now) {
		BatchExecutionRequestDetailDto current = locked(executionId);
		if (current.getExecutionStatus().isTerminal()) {
			return current;
		}
		MonitorDecision decision = monitorDecision(current, now);
		if (decision.expired()) {
			BatchExecutionRequestDetailDto expired = finish(current, BatchExecutionStatus.EXPIRED, now);
			notifyWarning(expired, BatchMessageConstants.START_OVERDUE_TITLE);
			return expired;
		}
		if (decision.title() == null
				|| Objects.equals(decision.confirmation(), current.getConfirmationStatus())) {
			return current;
		}
		BatchExecutionRequestDetailDto updated = current.toBuilder().confirmationStatus(decision.confirmation()).build();
		repository.saveControlState(updated);
		notifyWarning(updated, decision.title());
		return updated;
	}

	private MonitorDecision monitorDecision(BatchExecutionRequestDetailDto current, LocalDateTime now) {

		// 停止要求がある場合は、結果待ち期限より停止確認を優先して運用上の未確認理由を残す。
		if (current.getCancelRequestedAt() != null) {
			if (!now.isBefore(current.getCancelRequestedAt().plusSeconds(current.getCancelGraceSeconds()))) {
				return new MonitorDecision("stop_unconfirmed", BatchMessageConstants.STOP_UNCONFIRMED_TITLE, false);
			}
			return new MonitorDecision(null, null, false);
		}

		// 結果待ち期限の超過は確認状態だけを変える。RUNNINGを終端状態へ変えたり、自動再実行したりしない。
		if (current.getExecutionStatus() == BatchExecutionStatus.RUNNING) {
			if (current.getResultDeadlineAt() != null && !now.isBefore(current.getResultDeadlineAt())) {
				return new MonitorDecision("result_unconfirmed", BatchMessageConstants.RESULT_UNCONFIRMED_TITLE, false);
			}
			return new MonitorDecision(null, null, false);
		}
		if (!now.isBefore(current.getStartDeadlineAt())) {
			if (neverDispatched(current)) {
				return new MonitorDecision(null, null, true);
			}
			return new MonitorDecision("start_unconfirmed", BatchMessageConstants.START_UNCONFIRMED_TITLE, false);
		}
		return new MonitorDecision(null, null, false);
	}

	private void notifyWarning(BatchExecutionRequestDetailDto request, String title) {
		notifications.sendNotice(BrowserNotificationCreateDto.builder()
				.notificationType(NotificationType.SYSTEM_NOTICE).level(NotificationLevel.WARNING)
				.title(title)
				.message(BatchMessageConstants.EXECUTION_WARNING_MESSAGE.formatted(
						request.getExecutionId(), request.getBatchDefinitionId(), request.getTargetAgentId()))
				.sourceType(NotificationSourceType.BATCH_EXECUTION).sourceId(request.getExecutionId().toString())
				.linkPath("/alerts").build());
	}

	private BatchExecutionRequestDetailDto finish(BatchExecutionRequestDetailDto current,
			BatchExecutionStatus status, LocalDateTime now) {
		BatchExecutionRequestDetailDto updated = current.toBuilder().executionStatus(status).confirmationStatus("confirmed")
				.finishedAt(now).build();
		repository.saveControlState(updated);
		TBatchExecutionHistoriesRecord history = new TBatchExecutionHistoriesRecord();
		history.setBatchDefinitionId(updated.getBatchDefinitionId());
		history.setTestExecution(Boolean.TRUE.equals(updated.getTestExecution()));
		history.setExecutionStartedDt(updated.getStartedAt() != null ? updated.getStartedAt() : updated.getRequestedAt());
		history.setExecutionFinishedDt(now);
		history.setExecutionResult(status.getStoredName());
		history.setExitCode(updated.getExitCode());
		history.setLogPath(updated.getLogPath());
		history.setMessage(updated.getMessage());
		historyRepository.save(history);
		return updated;
	}

	private boolean neverDispatched(BatchExecutionRequestDetailDto request) {
		return request.getExecutionStatus() == BatchExecutionStatus.QUEUED
				&& request.getDispatchedAt() == null && request.getDispatchAttemptCount() == 0;
	}

	private boolean recentlyAttempted(LocalDateTime attemptedAt, LocalDateTime now) {
		return attemptedAt != null && attemptedAt.isAfter(now.minusSeconds(30));
	}

	private BatchExecutionRequestDetailDto locked(UUID executionId) {
		return repository.lockByExecutionId(executionId)
				.orElseThrow(() -> new ResourceNotFoundException("batch execution request", executionId));
	}

	private <T> T transaction(Supplier<T> action) {
		TransactionTemplate template = new TransactionTemplate(transactionManager);
		template.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		return template.execute(status -> action.get());
	}

	/** Agent認可の行ロックと結果反映を同じトランザクションに含める。 */
	private <T> T resultTransaction(Supplier<T> action) {
		return new TransactionTemplate(transactionManager).execute(status -> action.get());
	}
}
