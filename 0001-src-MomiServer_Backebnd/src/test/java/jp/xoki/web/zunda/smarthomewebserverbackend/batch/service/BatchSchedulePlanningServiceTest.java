package jp.xoki.web.zunda.smarthomewebserverbackend.batch.service;

import jp.xoki.web.zunda.jooq.tables.records.MBatchDefinitionsRecord;
import jp.xoki.web.zunda.jooq.tables.records.MBatchSchedulesRecord;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository.BatchDefinitionRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository.BatchScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.time.ApplicationClockFactory;

class BatchSchedulePlanningServiceTest {
	private final LocalDateTime midnight = LocalDateTime.of(2026, 9, 12, 0, 0);
	private BatchScheduleRepository schedules;
	private BatchDefinitionRepository definitions;
	private BatchExecutionRequestService requests;
	private BatchSchedulePlanningService planning;
	private MBatchSchedulesRecord schedule;
	private MBatchDefinitionsRecord definition;

	@BeforeEach
	void setUp() {
		schedules = mock(BatchScheduleRepository.class);
		definitions = mock(BatchDefinitionRepository.class);
		requests = mock(BatchExecutionRequestService.class);
		planning = new BatchSchedulePlanningService(schedules, definitions, requests, ApplicationClockFactory.systemClock());
		schedule = new MBatchSchedulesRecord();
		schedule.setId(1L);
		schedule.setBatchDefinitionId(2L);
		schedule.setEnabled(true);
		schedule.setCronExpression("0 1 * * *");
		definition = new MBatchDefinitionsRecord();
		definition.setId(2L);
		definition.setEnabled(true);
		when(schedules.findEnabled()).thenReturn(List.of(schedule));
		when(definitions.findById(2L)).thenReturn(Optional.of(definition));
	}

	@Test
	void entryPointUsesInjectedClockAndTruncatesSubsecondPrecision() {
		Clock clock = Clock.fixed(Instant.parse("2026-09-11T15:00:00.500Z"), ZoneId.of("Asia/Tokyo"));
		BatchSchedulePlanningService service = new BatchSchedulePlanningService(schedules, definitions, requests, clock);
		service.enqueueScheduledBatches();
		verify(requests).createScheduledRequest(schedule, definition, midnight.plusHours(1), midnight);
		verify(requests).createScheduledRequest(schedule, definition, midnight.plusDays(1).plusHours(1), midnight);
		verifyNoMoreInteractions(requests);
	}

	@Test
	void expandsTodayAndTomorrowInsteadOfCheckingCurrentTime() {
		planning.enqueueScheduledBatches(midnight);
		verify(requests).createScheduledRequest(schedule, definition, midnight.plusHours(1), midnight);
		verify(requests).createScheduledRequest(schedule, definition, midnight.plusDays(1).plusHours(1), midnight);
		verifyNoMoreInteractions(requests);
	}

	@Test
	void recoveryIncludesMissedTimesWithRecoveryCutoff() {
		LocalDateTime recovery = midnight.plusHours(19);
		planning.enqueueScheduledBatches(recovery);
		verify(requests).createScheduledRequest(schedule, definition, midnight.plusHours(1), recovery);
		verify(requests).createScheduledRequest(schedule, definition, midnight.plusDays(1).plusHours(1), recovery);
	}

	@ParameterizedTest
	@ValueSource(strings = {"2026-09-30T00:00:00", "2026-12-31T00:00:00", "2028-02-28T00:00:00"})
	void includesBothMidnightsAndExcludesThirdDayAcrossCalendarBoundaries(String value) {
		LocalDateTime start = LocalDateTime.parse(value);
		schedule.setCronExpression("0 0 * * *");
		planning.enqueueScheduledBatches(start);
		verify(requests).createScheduledRequest(schedule, definition, start, start);
		verify(requests).createScheduledRequest(schedule, definition, start.plusDays(1), start);
		verifyNoMoreInteractions(requests);
	}

	@Test
	void generatesEveryOccurrenceIncludingSixFieldCron() {
		schedule.setCronExpression("30 0 * * * *");
		planning.enqueueScheduledBatches(midnight);
		ArgumentCaptor<LocalDateTime> times = ArgumentCaptor.forClass(LocalDateTime.class);
		verify(requests, times(48)).createScheduledRequest(eq(schedule), eq(definition), times.capture(), eq(midnight));
		assertThat(times.getAllValues()).hasSize(48).doesNotHaveDuplicates()
				.startsWith(midnight.plusSeconds(30)).endsWith(midnight.plusDays(1).plusHours(23).plusSeconds(30));
	}

	@Test
	void respectsInclusiveStartAndEndDates() {
		schedule.setStartDate(midnight.toLocalDate().plusDays(1));
		schedule.setEndDate(midnight.toLocalDate().plusDays(1));
		planning.enqueueScheduledBatches(midnight);
		verify(requests).createScheduledRequest(schedule, definition, midnight.plusDays(1).plusHours(1), midnight);
		verifyNoMoreInteractions(requests);
	}

	@Test
	void excludesDatesAfterEndDate() {
		schedule.setEndDate(midnight.toLocalDate());
		planning.enqueueScheduledBatches(midnight);
		verify(requests).createScheduledRequest(schedule, definition, midnight.plusHours(1), midnight);
		verifyNoMoreInteractions(requests);
	}

	@Test
	void excludesDisabledOrMissingDefinitions() {
		definition.setEnabled(false);
		planning.enqueueScheduledBatches(midnight);
		when(definitions.findById(2L)).thenReturn(Optional.empty());
		planning.enqueueScheduledBatches(midnight);
		verifyNoInteractions(requests);
	}

	@Test
	void invalidCronDoesNotStopOtherSchedules() {
		MBatchSchedulesRecord invalid = new MBatchSchedulesRecord();
		invalid.setId(9L);
		invalid.setBatchDefinitionId(2L);
		invalid.setCronExpression("invalid cron");
		when(schedules.findEnabled()).thenReturn(List.of(invalid, schedule));
		planning.enqueueScheduledBatches(midnight);
		verify(requests, times(2)).createScheduledRequest(eq(schedule), eq(definition), any(), eq(midnight));
		verifyNoMoreInteractions(requests);
	}

	@Test
	void failedOccurrenceDoesNotStopTomorrow() {
		when(requests.createScheduledRequest(schedule, definition, midnight.plusHours(1), midnight))
				.thenThrow(new IllegalStateException("database unavailable"));
		planning.enqueueScheduledBatches(midnight);
		verify(requests).createScheduledRequest(schedule, definition, midnight.plusDays(1).plusHours(1), midnight);
	}
}
