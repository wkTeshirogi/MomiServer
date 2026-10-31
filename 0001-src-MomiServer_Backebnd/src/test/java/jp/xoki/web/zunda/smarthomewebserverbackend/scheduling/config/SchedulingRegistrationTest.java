package jp.xoki.web.zunda.smarthomewebserverbackend.scheduling.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.service.AgentAuthService;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.service.AgentMqttProvisioning;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.service.BatchExecutionRequestService;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.service.BatchSchedulePlanningService;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.service.ApiAccessLogService;
import jp.xoki.web.zunda.smarthomewebserverbackend.scheduling.scheduler.AgentAuthCleanupScheduler;
import jp.xoki.web.zunda.smarthomewebserverbackend.scheduling.scheduler.AgentMqttProvisioningScheduler;
import jp.xoki.web.zunda.smarthomewebserverbackend.scheduling.scheduler.ApiAccessLogCleanupScheduler;
import jp.xoki.web.zunda.smarthomewebserverbackend.scheduling.scheduler.BatchExecutionScheduler;
import jp.xoki.web.zunda.smarthomewebserverbackend.scheduling.scheduler.StatusWebSocketBroadcastScheduler;
import jp.xoki.web.zunda.smarthomewebserverbackend.scheduling.scheduler.StatusWebSocketTicketCleanupScheduler;
import jp.xoki.web.zunda.smarthomewebserverbackend.scheduling.scheduler.VehicleImportCleanupScheduler;
import jp.xoki.web.zunda.smarthomewebserverbackend.scheduling.scheduler.VehicleInspectionNotificationScheduler;
import jp.xoki.web.zunda.smarthomewebserverbackend.status.service.StatusWebSocketTicketService;
import jp.xoki.web.zunda.smarthomewebserverbackend.status.websocket.StatusWebSocketPublisher;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.imports.VehicleImportService;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.service.VehicleInspectionNotificationCheckService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.ScheduledTask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SchedulingRegistrationTest {

	private ApplicationContextRunner runner() {
		return new ApplicationContextRunner().withUserConfiguration(SchedulingConfig.class,
				AgentAuthCleanupScheduler.class, AgentMqttProvisioningScheduler.class, ApiAccessLogCleanupScheduler.class,
				StatusWebSocketBroadcastScheduler.class, StatusWebSocketTicketCleanupScheduler.class,
				VehicleImportCleanupScheduler.class, VehicleInspectionNotificationScheduler.class)
				.withPropertyValues("app.vehicle-import.cleanup-cron=0 30 2 * * *")
				.withBean(Clock.class, () -> Clock.fixed(Instant.parse("2026-10-26T00:00:00Z"), ZoneOffset.UTC))
				.withBean(BatchSchedulePlanningService.class, () -> mock(BatchSchedulePlanningService.class))
				.withBean(BatchExecutionRequestService.class, () -> mock(BatchExecutionRequestService.class))
				.withBean(AgentAuthService.class, () -> mock(AgentAuthService.class))
				.withBean(AgentMqttProvisioning.class, () -> mock(AgentMqttProvisioning.class))
				.withBean(ApiAccessLogService.class, () -> mock(ApiAccessLogService.class))
				.withBean(StatusWebSocketPublisher.class, () -> mock(StatusWebSocketPublisher.class))
				.withBean(StatusWebSocketTicketService.class, () -> mock(StatusWebSocketTicketService.class))
				.withBean(VehicleImportService.class, () -> mock(VehicleImportService.class))
				.withBean(VehicleInspectionNotificationCheckService.class, () -> mock(VehicleInspectionNotificationCheckService.class));
	}

	@Test
	void registersExactlyElevenDistinctTasksAndInvokesReadyHooks() {
		runner().run(context -> {
			Set<ScheduledTask> tasks = context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks();
			assertThat(tasks).hasSize(11);
			Set<String> entryPoints = new HashSet<>();
			for (ScheduledTask task : tasks) {

				// 実行状況追跡ラッパーの具体型ではなく、登録された処理名で重複と配置を検査する。
				String identity = task.getTask().getRunnable().toString();
				assertThat(identity).startsWith("jp.xoki.web.zunda.smarthomewebserverbackend.scheduling.scheduler.");
				assertThat(entryPoints.add(identity)).isTrue();
			}
			context.publishEvent(new ApplicationReadyEvent(new SpringApplication(), new String[0],
					context.getSourceApplicationContext(), Duration.ZERO));
			verify(context.getBean(BatchSchedulePlanningService.class)).enqueueScheduledBatches();
			verify(context.getBean(VehicleInspectionNotificationCheckService.class)).checkExpiries();
		});
	}

	@Test
	void vehicleFlagDisablesItsTimerAndReadyHookOnly() {
		runner().withPropertyValues("app.vehicle-inspection-notification.enabled=false").run(context -> {
			assertThat(context).doesNotHaveBean(VehicleInspectionNotificationScheduler.class);
			assertThat(context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks()).hasSize(10);
			context.publishEvent(new ApplicationReadyEvent(new SpringApplication(), new String[0],
					context.getSourceApplicationContext(), Duration.ZERO));
			verifyNoInteractions(context.getBean(VehicleInspectionNotificationCheckService.class));
			verify(context.getBean(BatchSchedulePlanningService.class)).enqueueScheduledBatches();
		});
	}

	@Test
	void globalFlagDisablesTimersAndBatchReadyHookButPreservesExistingVehicleReadyBehavior() {
		runner().withPropertyValues("app.scheduling.enabled=false").run(context -> {
			assertThat(context).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class);
			assertThat(context).doesNotHaveBean(BatchExecutionScheduler.class);
			context.publishEvent(new ApplicationReadyEvent(new SpringApplication(), new String[0],
					context.getSourceApplicationContext(), Duration.ZERO));
			verifyNoInteractions(context.getBean(BatchSchedulePlanningService.class), context.getBean(BatchExecutionRequestService.class),
					context.getBean(AgentAuthService.class), context.getBean(AgentMqttProvisioning.class), context.getBean(ApiAccessLogService.class),
					context.getBean(StatusWebSocketPublisher.class), context.getBean(StatusWebSocketTicketService.class), context.getBean(VehicleImportService.class));
			verify(context.getBean(VehicleInspectionNotificationCheckService.class)).checkExpiries();
		});
	}

	@Test
	void schedulingAnnotationsAreConfinedToEntryPointPackage() throws Exception {
		PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
		CachingMetadataReaderFactory readers = new CachingMetadataReaderFactory(resolver);
		Set<String> entryPoints = new HashSet<>();
		Set<String> configurations = new HashSet<>();
		for (Resource resource : resolver.getResources("classpath*:jp/xoki/web/zunda/smarthomewebserverbackend/**/*.class")) {
			AnnotationMetadata metadata = readers.getMetadataReader(resource).getAnnotationMetadata();
			for (MethodMetadata method : metadata.getAnnotatedMethods(Scheduled.class.getName())) {
				assertThat(method.getDeclaringClassName()).startsWith("jp.xoki.web.zunda.smarthomewebserverbackend.scheduling.scheduler.");
				entryPoints.add(method.getDeclaringClassName() + "." + method.getMethodName());
			}
			if (metadata.hasAnnotation(EnableScheduling.class.getName())) {
				configurations.add(metadata.getClassName());
			}
		}
		assertThat(entryPoints).hasSize(11);
		assertThat(configurations).containsExactly(SchedulingConfig.class.getName());
	}
}
