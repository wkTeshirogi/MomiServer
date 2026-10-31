package jp.xoki.web.zunda.smarthomewebserverbackend.scheduling.config;

import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import jp.xoki.web.zunda.jooq.tables.records.MBatchDefinitionsRecord;
import jp.xoki.web.zunda.jooq.tables.records.MBatchSchedulesRecord;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository.BatchDefinitionRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository.BatchScheduleRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.service.BatchExecutionRequestService;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.service.BatchSchedulePlanningService;
import jp.xoki.web.zunda.smarthomewebserverbackend.scheduling.scheduler.BatchExecutionScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SchedulingConfigTest {

	private ApplicationContextRunner runner() {
		return new ApplicationContextRunner()
				.withUserConfiguration(SchedulingConfig.class)
				.withBean(BatchSchedulePlanningService.class, () -> mock(BatchSchedulePlanningService.class))
				.withBean(BatchExecutionRequestService.class, () -> mock(BatchExecutionRequestService.class));
	}

	@Test
	void defaultConfigurationKeepsSchedulingEnabled() {
		runner().run(context -> {
			assertThat(context).hasSingleBean(BatchExecutionScheduler.class);
			assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class);
			assertThat(context).hasBean("taskScheduler");
			ThreadPoolTaskScheduler scheduler = context.getBean("taskScheduler", ThreadPoolTaskScheduler.class);
			assertThat(scheduler.getPoolSize()).isEqualTo(4);
			assertThat(scheduler.getThreadNamePrefix()).isEqualTo("batch-control-");
		});
	}

	@Test
	void explicitTrueKeepsSchedulingEnabled() {
		runner().withPropertyValues("app.scheduling.enabled=true").run(context -> {
			assertThat(context).hasSingleBean(BatchExecutionScheduler.class);
			assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class);
		});
	}

	@Test
	void readyEventUsesRealPlanningServiceToCreateTodayAndTomorrow() {
		BatchScheduleRepository schedules = mock(BatchScheduleRepository.class);
		BatchDefinitionRepository definitions = mock(BatchDefinitionRepository.class);
		BatchExecutionRequestService requests = mock(BatchExecutionRequestService.class);
		MBatchSchedulesRecord schedule = new MBatchSchedulesRecord();
		schedule.setId(1L);
		schedule.setBatchDefinitionId(2L);
		schedule.setCronExpression("0 1 * * *");
		MBatchDefinitionsRecord definition = new MBatchDefinitionsRecord();
		definition.setId(2L);
		definition.setEnabled(true);
		when(schedules.findEnabled()).thenReturn(List.of(schedule));
		when(definitions.findById(2L)).thenReturn(Optional.of(definition));
		new ApplicationContextRunner().withUserConfiguration(SchedulingConfig.class)
				.withBean(Clock.class, () -> Clock.fixed(Instant.parse("2026-10-25T15:00:00Z"), ZoneId.of("Asia/Tokyo")))
				.withBean(BatchScheduleRepository.class, () -> schedules)
				.withBean(BatchDefinitionRepository.class, () -> definitions)
				.withBean(BatchExecutionRequestService.class, () -> requests)
				.withBean(BatchSchedulePlanningService.class)
				.run(context -> {
					context.publishEvent(new ApplicationReadyEvent(new SpringApplication(), new String[0],
							context.getSourceApplicationContext(), Duration.ZERO));
					LocalDateTime midnight = LocalDateTime.of(2026, 10, 26, 0, 0);
					verify(requests).createScheduledRequest(schedule, definition, midnight.plusHours(1), midnight);
					verify(requests).createScheduledRequest(schedule, definition, midnight.plusDays(1).plusHours(1), midnight);
				});
	}

	@Test
	void disabledSchedulingDoesNotRegisterTimersOrReadyEventBatch() {
		runner().withPropertyValues("app.scheduling.enabled=false").run(context -> {
			assertThat(context).doesNotHaveBean(BatchExecutionScheduler.class);
			assertThat(context).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class);
			assertThat(context).doesNotHaveBean("taskScheduler");
			context.publishEvent(new ApplicationReadyEvent(new SpringApplication(), new String[0],
					context.getSourceApplicationContext(), Duration.ZERO));
			verifyNoInteractions(context.getBean(BatchSchedulePlanningService.class),
					context.getBean(BatchExecutionRequestService.class));
		});
	}
}
