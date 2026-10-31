package jp.xoki.web.zunda.smarthomewebserverbackend.scheduling.scheduler;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.service.AgentAuthService;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.service.AgentMqttProvisioning;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.service.BatchExecutionRequestService;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.service.BatchSchedulePlanningService;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.service.ApiAccessLogService;
import jp.xoki.web.zunda.smarthomewebserverbackend.status.service.StatusWebSocketTicketService;
import jp.xoki.web.zunda.smarthomewebserverbackend.status.websocket.StatusWebSocketPublisher;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.imports.VehicleImportService;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.service.VehicleInspectionNotificationCheckService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SchedulerEntryPointTest {

	@Test
	void delegatesEveryEntryPointToItsOwningService() {
		BatchSchedulePlanningService planning = mock(BatchSchedulePlanningService.class);
		BatchExecutionRequestService requests = mock(BatchExecutionRequestService.class);
		AgentAuthService auth = mock(AgentAuthService.class);
		AgentMqttProvisioning mqtt = mock(AgentMqttProvisioning.class);
		ApiAccessLogService logs = mock(ApiAccessLogService.class);
		StatusWebSocketPublisher publisher = mock(StatusWebSocketPublisher.class);
		StatusWebSocketTicketService tickets = mock(StatusWebSocketTicketService.class);
		VehicleImportService imports = mock(VehicleImportService.class);
		VehicleInspectionNotificationCheckService inspections = mock(VehicleInspectionNotificationCheckService.class);
		Clock clock = Clock.fixed(Instant.parse("2026-10-26T00:00:00Z"), ZoneOffset.UTC);
		BatchExecutionScheduler batch = new BatchExecutionScheduler(planning, requests);

		batch.enqueueScheduledBatches();
		batch.redispatchQueuedRequests();
		batch.monitorExecutionRequests();
		batch.redispatchCancellations();
		new AgentAuthCleanupScheduler(auth).cleanup();
		new AgentMqttProvisioningScheduler(mqtt).reconcile();
		new ApiAccessLogCleanupScheduler(logs, clock).cleanupExpired();
		new StatusWebSocketBroadcastScheduler(publisher).publish();
		new StatusWebSocketTicketCleanupScheduler(tickets).cleanup();
		new VehicleImportCleanupScheduler(imports).cleanupExpired();
		new VehicleInspectionNotificationScheduler(inspections).checkExpiries();

		verify(planning).enqueueScheduledBatches();
		verify(requests).redispatchQueuedRequests();
		verify(requests).monitorRequests();
		verify(requests).redispatchCancellations();
		verify(auth).cleanup();
		verify(mqtt).reconcile();
		verify(logs).cleanupExpired(OffsetDateTime.now(clock));
		verify(publisher).publish();
		verify(tickets).cleanup();
		verify(imports).cleanupExpired();
		verify(inspections).checkExpiries();
		verifyNoMoreInteractions(planning, requests, auth, mqtt, logs, publisher, tickets, imports, inspections);
	}

	@ParameterizedTest
	@MethodSource("cronSchedules")
	void preservesCronExpressionsAndJapaneseTimeZone(Class<?> owner, String method, String cron) throws Exception {
		Scheduled scheduled = owner.getMethod(method).getAnnotation(Scheduled.class);
		assertThat(scheduled.cron()).isEqualTo(cron);
		assertThat(scheduled.zone()).isEqualTo("Asia/Tokyo");
		assertThat(scheduled.fixedDelay()).isEqualTo(-1);
	}

	private static Stream<Arguments> cronSchedules() {
		return Stream.of(
				Arguments.of(BatchExecutionScheduler.class, "enqueueScheduledBatches", "0 0 0 * * *"),
				Arguments.of(ApiAccessLogCleanupScheduler.class, "cleanupExpired", "${app.api-access-log.cleanup-cron:0 15 2 * * *}"),
				Arguments.of(VehicleImportCleanupScheduler.class, "cleanupExpired", "${app.vehicle-import.cleanup-cron}"),
				Arguments.of(VehicleInspectionNotificationScheduler.class, "checkExpiries", "${app.vehicle-inspection-notification.cron:0 0 9 * * *}"));
	}

	@ParameterizedTest
	@MethodSource("fixedSchedules")
	void preservesFixedDelayAndInitialDelay(Class<?> owner, String method, long interval) throws Exception {
		Scheduled scheduled = owner.getMethod(method).getAnnotation(Scheduled.class);
		assertThat(scheduled.fixedDelay()).isEqualTo(interval);
		assertThat(scheduled.initialDelay()).isEqualTo(interval);
		assertThat(scheduled.timeUnit()).isEqualTo(TimeUnit.MILLISECONDS);
	}

	private static Stream<Arguments> fixedSchedules() {
		return Stream.of(
				Arguments.of(AgentAuthCleanupScheduler.class, "cleanup", 3600000L),
				Arguments.of(AgentMqttProvisioningScheduler.class, "reconcile", 5000L),
				Arguments.of(StatusWebSocketTicketCleanupScheduler.class, "cleanup", 60000L));
	}

	@Test
	void preservesConfigurableDelays() throws Exception {
		assertThat(BatchExecutionScheduler.class.getMethod("redispatchQueuedRequests").getAnnotation(Scheduled.class).fixedDelayString())
				.isEqualTo("${batch.execution.dispatch-retry-delay-ms:30000}");
		assertThat(BatchExecutionScheduler.class.getMethod("monitorExecutionRequests").getAnnotation(Scheduled.class).fixedDelayString())
				.isEqualTo("${batch.execution.timeout-scan-delay-ms:30000}");
		assertThat(BatchExecutionScheduler.class.getMethod("redispatchCancellations").getAnnotation(Scheduled.class).fixedDelayString())
				.isEqualTo("${batch.execution.cancel-retry-delay-ms:30000}");
		Scheduled broadcast = StatusWebSocketBroadcastScheduler.class.getMethod("publish").getAnnotation(Scheduled.class);
		assertThat(broadcast.fixedDelayString()).isEqualTo("${app.status.websocket.broadcast-interval-ms:5000}");
		assertThat(broadcast.initialDelayString()).isEqualTo(broadcast.fixedDelayString());
	}

	@Test
	void preservesBothApplicationReadyHooks() throws Exception {
		for (Method method : List.of(BatchExecutionScheduler.class.getMethod("enqueueScheduledBatches"),
				VehicleInspectionNotificationScheduler.class.getMethod("checkExpiries"))) {
			assertThat(method.getAnnotation(EventListener.class).value()).containsExactly(ApplicationReadyEvent.class);
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void invokesTransactionalImportServiceProxy(boolean fails) {
		VehicleImportService target = mock(VehicleImportService.class);
		PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
		TransactionStatus status = mock(TransactionStatus.class);
		when(manager.getTransaction(any())).thenReturn(status);
		IllegalStateException failure = new IllegalStateException("cleanup failed");
		if (fails) {
			doThrow(failure).when(target).cleanupExpired();
		}
		TransactionInterceptor interceptor = new TransactionInterceptor();
		interceptor.setTransactionManager(manager);
		interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
		ProxyFactory factory = new ProxyFactory(target);
		factory.addAdvice(interceptor);
		VehicleImportCleanupScheduler scheduler = new VehicleImportCleanupScheduler((VehicleImportService) factory.getProxy());

		if (fails) {
			assertThatThrownBy(scheduler::cleanupExpired).isSameAs(failure);
		} else {
			scheduler.cleanupExpired();
		}
		InOrder order = inOrder(manager, target);
		order.verify(manager).getTransaction(any());
		order.verify(target).cleanupExpired();
		if (fails) {
			order.verify(manager).rollback(status);
		} else {
			order.verify(manager).commit(status);
		}
	}
}
