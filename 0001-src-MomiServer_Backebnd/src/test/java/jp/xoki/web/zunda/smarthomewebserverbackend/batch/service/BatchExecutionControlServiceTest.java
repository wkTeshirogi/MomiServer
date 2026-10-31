package jp.xoki.web.zunda.smarthomewebserverbackend.batch.service;

import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.AgentBatchCompleteRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchExecutionRequestDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.model.BatchExecutionStatus;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository.BatchExecutionHistoryRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository.BatchExecutionRequestRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.BrowserNotificationCreateDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.util.Notifications;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.time.ApplicationClockFactory;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;

class BatchExecutionControlServiceTest {

	private final UUID executionId = UUID.randomUUID();
	private final LocalDateTime now = LocalDateTime.now(ApplicationClockFactory.systemClock());
	private final AtomicReference<BatchExecutionRequestDetailDto> state = new AtomicReference<>();
	private BatchExecutionRequestRepository repository;
	private BatchExecutionHistoryRepository histories;
	private BatchCommandPublishService publisher;
	private Notifications notifications;
	private PlatformTransactionManager transactions;
	private BatchExecutionControlService service;
	private BatchMaintenanceService maintenance;
	@TempDir
	Path directory;

	@BeforeEach
	void setUp() {
		repository = mock(BatchExecutionRequestRepository.class);
		histories = mock(BatchExecutionHistoryRepository.class);
		publisher = mock(BatchCommandPublishService.class);
		notifications = mock(Notifications.class);
		transactions = mock(PlatformTransactionManager.class);
		when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
		when(repository.lockByExecutionId(executionId)).thenAnswer(call -> Optional.of(state.get()));
		doAnswer(call -> { state.set(call.getArgument(0)); return null; }).when(repository).saveControlState(any());
		maintenance = new BatchMaintenanceService(directory.resolve("maintenance.flag").toString());
		service = new BatchExecutionControlService(repository, histories, publisher, notifications, transactions, ApplicationClockFactory.systemClock(), maintenance);
		state.set(BatchExecutionRequestDetailDto.builder().id(1L).executionId(executionId)
				.batchDefinitionId(2L).targetAgentId(3L).testExecution(false)
				.executionStatus(BatchExecutionStatus.QUEUED).confirmationStatus("pending")
				.requestedAt(now.minusHours(2)).startDeadlineAt(now.plusMinutes(10))
				.dispatchAttemptCount(0).resultWaitTimeoutSeconds(3600L).cancelGraceSeconds(30).build());
	}

	@Test
	void maintenanceDefersStartsAndDispatchButAllowsCompletionAndCancellation() {
		maintenance.setPaused(true);
		assertThat(service.start(executionId, null).getExecutionStatus()).isEqualTo(BatchExecutionStatus.QUEUED);
		assertThat(service.start(executionId, now).getExecutionStatus()).isEqualTo(BatchExecutionStatus.QUEUED);
		service.dispatch(executionId, now);
		verifyNoInteractions(publisher, histories);
		verify(repository, never()).saveControlState(any());
		service.requestCancellation(executionId, "admin");
		assertThat(state.get().getExecutionStatus()).isEqualTo(BatchExecutionStatus.CANCELLED);
		state.set(state.get().toBuilder().executionStatus(BatchExecutionStatus.RUNNING).cancelRequestedAt(null).build());
		assertThat(service.start(executionId, null).getExecutionStatus()).isEqualTo(BatchExecutionStatus.RUNNING);
		AgentBatchCompleteRequest result = new AgentBatchCompleteRequest();
		result.setExecutionStatus(BatchExecutionStatus.SUCCESS);
		assertThat(service.complete(executionId, result).getExecutionStatus()).isEqualTo(BatchExecutionStatus.SUCCESS);
		verify(histories, times(2)).save(any());
	}

	@Test
	void explicitResumePermitsDeferredStart() {
		maintenance.setPaused(true);
		service.start(executionId, null);
		maintenance.setPaused(false);
		assertThat(service.start(executionId, null).getExecutionStatus()).isEqualTo(BatchExecutionStatus.RUNNING);
		verify(repository).saveControlState(any());
	}

	@Test
	void resultDeadlineLeavesRunningAndNotifiesOnlyOnce() {
		state.set(state.get().toBuilder().executionStatus(BatchExecutionStatus.RUNNING)
				.startedAt(now.minusHours(2)).resultDeadlineAt(now).build());
		service.monitor(executionId, now);
		service.monitor(executionId, now.plusMinutes(1));
		assertThat(state.get().getExecutionStatus()).isEqualTo(BatchExecutionStatus.RUNNING);
		assertThat(state.get().getConfirmationStatus()).isEqualTo("result_unconfirmed");
		assertThat(state.get().getFinishedAt()).isNull();
		ArgumentCaptor<BrowserNotificationCreateDto> notice = ArgumentCaptor.forClass(BrowserNotificationCreateDto.class);
		verify(notifications).sendNotice(notice.capture());
		assertThat(notice.getValue().getTitle()).isEqualTo("バッチの実行結果を確認できません");
		assertThat(notice.getValue().getMessage()).isEqualTo(
				"実行ID: " + executionId + " / 定義ID: 2 / エージェントID: 3。期限超過による自動再実行は行いません。");
		verifyNoInteractions(histories, publisher);
	}

	@Test
	void executionTimeoutAloneDoesNotFinishOrWarn() {
		state.set(state.get().toBuilder().executionStatus(BatchExecutionStatus.RUNNING)
				.timeoutSeconds(1).startedAt(now.minusMinutes(10)).resultDeadlineAt(now.plusMinutes(30)).build());
		service.monitor(executionId, now);
		verify(repository, never()).saveControlState(any());
		verifyNoInteractions(histories, notifications);
	}

	@Test
	void startDeadlineExpiresOnlyNeverDispatchedRequests() {
		state.set(state.get().toBuilder().startDeadlineAt(now).build());
		service.monitor(executionId, now);
		service.monitor(executionId, now.plusMinutes(1));
		assertThat(state.get().getExecutionStatus()).isEqualTo(BatchExecutionStatus.EXPIRED);
		verify(histories, times(1)).save(any());
		verify(notifications, times(1)).sendNotice(any(BrowserNotificationCreateDto.class));
	}

	@Test
	void dispatchedDeadlineRemainsUnconfirmed() {
		state.set(state.get().toBuilder().executionStatus(BatchExecutionStatus.DISPATCHED)
				.dispatchAttemptCount(1).startDeadlineAt(now).build());
		service.monitor(executionId, now);
		assertThat(state.get().getExecutionStatus()).isEqualTo(BatchExecutionStatus.DISPATCHED);
		assertThat(state.get().getConfirmationStatus()).isEqualTo("start_unconfirmed");
		verifyNoInteractions(histories);
	}

	@Test
	void legacyFailedDispatchIsNotAssumedNeverSent() {
		state.set(state.get().toBuilder().dispatchAttemptCount(1).startDeadlineAt(now).build());
		service.monitor(executionId, now);
		assertThat(state.get().getExecutionStatus()).isEqualTo(BatchExecutionStatus.QUEUED);
		assertThat(state.get().getConfirmationStatus()).isEqualTo("start_unconfirmed");
	}

	@Test
	void duplicateStartPreservesFirstReceiptAndDeadline() {
		LocalDateTime startedAt = now.minusMinutes(2);
		BatchExecutionRequestDetailDto first = service.start(executionId, startedAt);
		BatchExecutionRequestDetailDto again = service.start(executionId, now);
		assertThat(again.getStartedAt()).isEqualTo(startedAt);
		assertThat(again.getStartReceivedAt()).isEqualTo(first.getStartReceivedAt());
		assertThat(again.getResultDeadlineAt()).isEqualTo(first.getResultDeadlineAt());
		verify(repository, times(1)).saveControlState(any());
	}

	@Test
	void lateStartNotificationUsesReceiptForResultDeadline() {
		state.set(state.get().toBuilder().executionStatus(BatchExecutionStatus.DISPATCHED)
				.startDeadlineAt(now.minusMinutes(5)).confirmationStatus("start_unconfirmed").build());
		BatchExecutionRequestDetailDto updated = service.start(executionId, now.minusMinutes(10));
		assertThat(updated.getExecutionStatus()).isEqualTo(BatchExecutionStatus.RUNNING);
		assertThat(updated.getConfirmationStatus()).isEqualTo("pending");
		assertThat(updated.getResultDeadlineAt()).isEqualTo(updated.getStartReceivedAt().plusHours(1));
	}

	@Test
	void actualStartAfterDeadlineIsRejected() {
		state.set(state.get().toBuilder().startDeadlineAt(now.minusMinutes(5)).build());
		assertThatThrownBy(() -> service.start(executionId, now)).isInstanceOf(IllegalArgumentException.class);
		verify(transactions).rollback(any());
	}

	@Test
	void delayedResultResolvesUnconfirmedAndDuplicateDoesNotAddHistory() {
		state.set(state.get().toBuilder().executionStatus(BatchExecutionStatus.RUNNING)
				.confirmationStatus("result_unconfirmed").build());
		service.complete(executionId, result(BatchExecutionStatus.SUCCESS));
		service.complete(executionId, result(BatchExecutionStatus.SUCCESS));
		assertThat(state.get().getConfirmationStatus()).isEqualTo("confirmed");
		assertThat(state.get().getExecutionStatus()).isEqualTo(BatchExecutionStatus.SUCCESS);
		verify(histories, times(1)).save(any());
	}

	@Test
	void conflictingTerminalResultCannotOverwriteAcceptedResult() {
		service.complete(executionId, result(BatchExecutionStatus.SUCCESS));
		AgentBatchCompleteRequest exceptionInput1 = result(BatchExecutionStatus.FAILED);
		assertThatThrownBy(() -> service.complete(executionId, exceptionInput1))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(state.get().getExecutionStatus()).isEqualTo(BatchExecutionStatus.SUCCESS);
		verify(histories, times(1)).save(any());
	}

	@Test
	void unsentCancellationFinishesWithoutPublishing() {
		service.requestCancellation(executionId, "admin");
		assertThat(state.get().getExecutionStatus()).isEqualTo(BatchExecutionStatus.CANCELLED);
		verifyNoInteractions(publisher);
		verify(histories).save(any());
	}

	@Test
	void runningCancellationWaitsForStopAndWarnsOnce() {
		state.set(state.get().toBuilder().executionStatus(BatchExecutionStatus.RUNNING).build());
		service.requestCancellation(executionId, "admin");
		LocalDateTime deadline = state.get().getCancelRequestedAt().plusSeconds(30);
		service.monitor(executionId, deadline);
		service.monitor(executionId, deadline.plusSeconds(30));
		assertThat(state.get().getExecutionStatus()).isEqualTo(BatchExecutionStatus.RUNNING);
		assertThat(state.get().getConfirmationStatus()).isEqualTo("stop_unconfirmed");
		verify(notifications, times(1)).sendNotice(any(BrowserNotificationCreateDto.class));
		verifyNoInteractions(histories);
	}

	@Test
	void definitionDisableDoesNotCancelAlreadyRunningExecution() {
		state.set(state.get().toBuilder().executionStatus(BatchExecutionStatus.RUNNING).build());

		BatchExecutionRequestDetailDto result = service.requestCancellationIfNotStarted(executionId, "definition-disabled");

		assertThat(result.getExecutionStatus()).isEqualTo(BatchExecutionStatus.RUNNING);
		assertThat(result.getCancelRequestedAt()).isNull();
	}

	@Test
	void cancellationRequestDoesNotExtendItsDeadline() {
		state.set(state.get().toBuilder().executionStatus(BatchExecutionStatus.RUNNING).build());
		BatchExecutionRequestDetailDto first = service.requestCancellation(executionId, "admin");
		BatchExecutionRequestDetailDto again = service.requestCancellation(executionId, "other");
		assertThat(again.getCancelRequestedAt()).isEqualTo(first.getCancelRequestedAt());
		assertThat(again.getCancelRequestedBy()).isEqualTo("admin");
	}

	@Test
	void normalCompletionWinsWhileCancellationIsPending() {
		state.set(state.get().toBuilder().executionStatus(BatchExecutionStatus.RUNNING).build());
		service.requestCancellation(executionId, "admin");
		service.complete(executionId, result(BatchExecutionStatus.SUCCESS));
		assertThat(state.get().getExecutionStatus()).isEqualTo(BatchExecutionStatus.SUCCESS);
	}

	@Test
	void timeoutNeedsStopConfirmation() {
		AgentBatchCompleteRequest exceptionInput2 = result(BatchExecutionStatus.TIMEOUT);
		assertThatThrownBy(() -> service.complete(executionId, exceptionInput2))
				.isInstanceOf(IllegalArgumentException.class);
		AgentBatchCompleteRequest result = result(BatchExecutionStatus.TIMEOUT);
		result.setStopConfirmed(true);
		service.complete(executionId, result);
		assertThat(state.get().getExecutionStatus()).isEqualTo(BatchExecutionStatus.TIMEOUT);
	}

	@Test
	void cancellationNeedsConfirmedStop() {
		state.set(state.get().toBuilder().executionStatus(BatchExecutionStatus.RUNNING).build());
		service.requestCancellation(executionId, "admin");
		AgentBatchCompleteRequest exceptionInput3 = result(BatchExecutionStatus.CANCELLED);
		assertThatThrownBy(() -> service.complete(executionId, exceptionInput3))
				.isInstanceOf(IllegalArgumentException.class);
		AgentBatchCompleteRequest result = result(BatchExecutionStatus.CANCELLED);
		result.setStopConfirmed(true);
		service.complete(executionId, result);
		assertThat(state.get().getExecutionStatus()).isEqualTo(BatchExecutionStatus.CANCELLED);
	}

	@Test
	void expiryRequiresNonStartConfirmationAndElapsedDeadline() {
		state.set(state.get().toBuilder().executionStatus(BatchExecutionStatus.DISPATCHED)
				.startDeadlineAt(now.minusMinutes(1)).build());
		AgentBatchCompleteRequest exceptionInput4 = result(BatchExecutionStatus.EXPIRED);
		assertThatThrownBy(() -> service.complete(executionId, exceptionInput4))
				.isInstanceOf(IllegalArgumentException.class);
		AgentBatchCompleteRequest result = result(BatchExecutionStatus.EXPIRED);
		result.setNotStarted(true);
		service.complete(executionId, result);
		assertThat(state.get().getExecutionStatus()).isEqualTo(BatchExecutionStatus.EXPIRED);
	}

	@Test
	void dispatchIntentIsCommittedBeforeNetworkAndFailureKeepsDispatched() {
		when(publisher.isEnabled()).thenReturn(true);
		when(publisher.publish(any())).thenThrow(new IllegalStateException("connection lost"));
		service.dispatch(executionId, now);
		InOrder order = inOrder(transactions, publisher);
		order.verify(publisher).isEnabled();
		order.verify(transactions).getTransaction(any());
		order.verify(transactions).commit(any());
		order.verify(publisher).publish(any());
		assertThat(state.get().getExecutionStatus()).isEqualTo(BatchExecutionStatus.DISPATCHED);
		assertThat(state.get().getDispatchAttemptCount()).isEqualTo(1);
	}

	@Test
	void dispatchIsThrottledAndNeverSentAfterStartDeadline() {
		when(publisher.isEnabled()).thenReturn(true);
		service.dispatch(executionId, now);
		service.dispatch(executionId, now.plusSeconds(29));
		service.dispatch(executionId, now.plusSeconds(30));
		service.dispatch(executionId, now.plusMinutes(10));
		verify(publisher, times(2)).publish(any());
	}

	@Test
	void disabledMqttDoesNotMarkUnsentRequestAsDispatched() {
		service.dispatch(executionId, now);
		assertThat(state.get().getExecutionStatus()).isEqualTo(BatchExecutionStatus.QUEUED);
		verifyNoInteractions(repository);
	}

	@Test
	void cancelRetransmissionStopsAfterConfirmedCompletion() {
		when(publisher.isEnabled()).thenReturn(true);
		state.set(state.get().toBuilder().executionStatus(BatchExecutionStatus.RUNNING).build());
		service.requestCancellation(executionId, "admin");
		service.dispatchCancellation(executionId, now);
		service.dispatchCancellation(executionId, now.plusSeconds(29));
		service.dispatchCancellation(executionId, now.plusSeconds(30));
		service.complete(executionId, result(BatchExecutionStatus.SUCCESS));
		service.dispatchCancellation(executionId, now.plusSeconds(60));
		verify(publisher, times(2)).publishCancellation(any());
	}

	@Test
	void futureScheduledRequestIsNotSentEvenWhenDirectlyDispatched() {
		when(publisher.isEnabled()).thenReturn(true);
		state.set(state.get().toBuilder().scheduledFireTime(now.plusDays(1))
				.startDeadlineAt(now.plusDays(1).plusHours(1)).build());
		service.dispatch(executionId, now);
		service.monitor(executionId, now);
		verify(publisher, never()).publish(any());
		verify(repository, never()).saveControlState(any());
		verifyNoInteractions(histories, notifications);
	}

	@Test
	void precreatedRequestIsSentAtScheduledTimeAndNotBefore() {
		when(publisher.isEnabled()).thenReturn(true);
		state.set(state.get().toBuilder().scheduledFireTime(now).build());
		service.dispatch(executionId, now.minusNanos(1));
		verify(publisher, never()).publish(any());
		service.dispatch(executionId, now);
		verify(publisher).publish(any());
	}

	@Test
	void normalPollingDelayCanDispatchPrecreatedRequestWithinStartDeadline() {
		when(publisher.isEnabled()).thenReturn(true);
		state.set(state.get().toBuilder().scheduledFireTime(now.minusSeconds(29)).build());
		service.dispatch(executionId, now);
		verify(publisher).publish(any());
	}

	@Test
	void missedRequestRecordedAsExpiredCannotBeSentOrReopened() {
		when(publisher.isEnabled()).thenReturn(true);
		state.set(state.get().toBuilder().scheduledFireTime(now.minusMinutes(1))
				.executionStatus(BatchExecutionStatus.EXPIRED).confirmationStatus("confirmed").finishedAt(now).build());
		service.dispatch(executionId, now);
		service.monitor(executionId, now);
		service.start(executionId, now);
		verify(publisher, never()).publish(any());
		verify(repository, never()).saveControlState(any());
		verifyNoInteractions(histories, notifications);
	}

	private AgentBatchCompleteRequest result(BatchExecutionStatus status) {
		AgentBatchCompleteRequest result = new AgentBatchCompleteRequest();
		result.setExecutionStatus(status);
		return result;
	}
}
