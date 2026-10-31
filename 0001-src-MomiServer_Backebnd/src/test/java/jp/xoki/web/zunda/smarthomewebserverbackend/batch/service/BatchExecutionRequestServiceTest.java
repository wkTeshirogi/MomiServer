package jp.xoki.web.zunda.smarthomewebserverbackend.batch.service;

import jp.xoki.web.zunda.jooq.tables.records.MBatchDefinitionsRecord;
import jp.xoki.web.zunda.jooq.tables.records.MBatchSchedulesRecord;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.model.BatchExecutionStatus;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchExecutionRequestCreateRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchExecutionRequestDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository.BatchDefinitionRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository.BatchExecutionRequestRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.BatchGlobalSettingDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.service.BatchGlobalSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.time.ApplicationClockFactory;

class BatchExecutionRequestServiceTest {
	private BatchDefinitionRepository definitions;
	private BatchExecutionRequestRepository requests;
	private BatchGlobalSettingService settings;
	private BatchExecutionControlService control;
	private BatchExecutionRequestService service;
	private MBatchDefinitionsRecord definition;

	@BeforeEach
	void setUp() {
		definitions = mock(BatchDefinitionRepository.class);
		requests = mock(BatchExecutionRequestRepository.class);
		settings = mock(BatchGlobalSettingService.class);
		control = mock(BatchExecutionControlService.class);
		service = new BatchExecutionRequestService(definitions, requests, settings, control, ApplicationClockFactory.systemClock());
		definition = new MBatchDefinitionsRecord();
		definition.setId(2L);
		definition.setEnabled(true);
		definition.setExecutionCommand("test-command");
		definition.setMaxRetryCount(2);
		when(definitions.lockById(2L)).thenReturn(Optional.of(definition));
		when(settings.detail()).thenReturn(BatchGlobalSettingDetailDto.builder()
				.settingVersion(4L).maxConcurrentExecutions(3).defaultTimeoutSeconds(1800)
				.resultRetryIntervalSeconds(300).resultRetryCount(5).unsentResultRetentionDays(7)
				.resultWaitTimeoutSeconds(3600).startWaitTimeoutSeconds(3600).cancelGraceSeconds(30).build());
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 600})
	void snapshotsGlobalSettingsAndUsesIndividualTimeoutWhenPresent(int timeout) {
		definition.setTimeoutSeconds(timeout);
		UUID executionId = UUID.randomUUID();
		BatchExecutionRequestDetailDto saved = BatchExecutionRequestDetailDto.builder().id(1L).executionId(executionId).build();
		when(requests.create(any())).thenReturn(1L);
		when(requests.findById(1L)).thenReturn(Optional.of(saved));
		when(requests.findByExecutionId(executionId)).thenReturn(Optional.of(saved));
		BatchExecutionRequestCreateRequest input = new BatchExecutionRequestCreateRequest();
		input.setTargetAgentId(3L);
		service.createManualRequest(2L, input, "admin");
		ArgumentCaptor<BatchExecutionRequestDetailDto> captor = ArgumentCaptor.forClass(BatchExecutionRequestDetailDto.class);
		verify(requests).create(captor.capture());
		BatchExecutionRequestDetailDto actual = captor.getValue();
		assertThat(actual.getTimeoutSeconds()).isEqualTo(timeout == 0 ? 1800 : 600);
		assertThat(actual.getSettingVersion()).isEqualTo(4L);
		assertThat(actual.getResultRetryCount()).isEqualTo(5);
		assertThat(actual.getMaxRetryCount()).isEqualTo(2);
		assertThat(actual.getStartDeadlineAt()).isEqualTo(actual.getRequestedAt().plusHours(1));
		assertThat(actual.getResultWaitTimeoutSeconds()).isEqualTo(3600L);
		verifyNoInteractions(control);
	}

	@Test
	void rejectsIndividualTimeoutAtOrAboveResultWait() {
		definition.setTimeoutSeconds(3600);
		BatchExecutionRequestCreateRequest input = new BatchExecutionRequestCreateRequest();
		input.setTargetAgentId(3L);
		assertThatThrownBy(() -> service.createManualRequest(2L, input, "admin"))
				.isInstanceOf(IllegalArgumentException.class);
		verifyNoInteractions(requests, control);
	}

	@ParameterizedTest
	@ValueSource(ints = {-1, 0, 1, 86400})
	void plannedRequestsUseScheduledDeadlineAndPastRequestsAreNeverQueued(int offsetSeconds) {
		LocalDateTime planningTime = LocalDateTime.of(2026, 9, 12, 0, 0);
		LocalDateTime fireTime = planningTime.plusSeconds(offsetSeconds);
		MBatchSchedulesRecord schedule = new MBatchSchedulesRecord();
		schedule.setId(7L);
		schedule.setTargetAgentId(3L);
		when(requests.create(any())).thenReturn(1L);
		when(requests.findById(1L)).thenReturn(Optional.of(BatchExecutionRequestDetailDto.builder().id(1L).build()));
		service.createScheduledRequest(schedule, definition, fireTime, planningTime);
		ArgumentCaptor<BatchExecutionRequestDetailDto> captor = ArgumentCaptor.forClass(BatchExecutionRequestDetailDto.class);
		verify(requests).create(captor.capture());
		BatchExecutionRequestDetailDto actual = captor.getValue();
		assertThat(actual.getScheduleId()).isEqualTo(7L);
		assertThat(actual.getRequestedType()).isEqualTo("schedule");
		assertThat(actual.getScheduledFireTime()).isEqualTo(fireTime);
		assertThat(actual.getRequestedAt()).isEqualTo(planningTime);
		assertThat(actual.getStartDeadlineAt()).isEqualTo(fireTime.plusHours(1));
		assertThat(actual.getExecutionStatus()).isEqualTo(offsetSeconds < 0 ? BatchExecutionStatus.EXPIRED : BatchExecutionStatus.QUEUED);
		assertThat(actual.getConfirmationStatus()).isEqualTo(offsetSeconds < 0 ? "confirmed" : "pending");
		if (offsetSeconds < 0) {
			assertThat(actual.getFinishedAt()).isEqualTo(planningTime);
			assertThat(actual.getMessage()).contains("execution skipped");
		} else {
			assertThat(actual.getFinishedAt()).isNull();
			assertThat(actual.getMessage()).isNull();
		}
		verifyNoInteractions(control);
	}

	@Test
	void duplicatePlanningDoesNotUpdateOrDispatchExistingRequest() {
		MBatchSchedulesRecord schedule = new MBatchSchedulesRecord();
		schedule.setId(7L);
		LocalDateTime planningTime = LocalDateTime.of(2026, 9, 12, 0, 0);
		when(requests.create(any())).thenReturn(null);
		assertThat(service.createScheduledRequest(schedule, definition, planningTime.plusHours(1), planningTime)).isNull();
		verify(requests).create(any());
		verifyNoMoreInteractions(requests);
		verifyNoInteractions(control);
	}
}
