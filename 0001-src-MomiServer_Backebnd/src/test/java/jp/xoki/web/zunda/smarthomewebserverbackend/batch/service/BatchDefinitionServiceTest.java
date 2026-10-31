package jp.xoki.web.zunda.smarthomewebserverbackend.batch.service;

import jp.xoki.web.zunda.jooq.tables.records.MBatchDefinitionsRecord;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchDefinitionRegistRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository.BatchDefinitionRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository.BatchExecutionRequestRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.OptimisticLockConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchDefinitionDetailDto;

@ExtendWith(MockitoExtension.class)
class BatchDefinitionServiceTest {

	@Mock
	private BatchDefinitionRepository definitionRepository;

	@Mock
	private BatchExecutionRequestRepository executionRequestRepository;

	@Mock
	private BatchExecutionControlService executionControlService;

	private BatchDefinitionService service;

	@BeforeEach
	void setUp() {
		service = new BatchDefinitionService(
				definitionRepository, executionRequestRepository, executionControlService);
	}

	@Test
	void staleVersionIsRejectedWithoutUpdating() {
		MBatchDefinitionsRecord current = definition(10L, 3L, true, "active");
		when(definitionRepository.findById(10L)).thenReturn(java.util.Optional.of(current));

		BatchDefinitionRegistRequest exceptionInput1 = request(10L, 2L, true, "active");
		assertThatThrownBy(() -> service.save(exceptionInput1))
				.isInstanceOf(OptimisticLockConflictException.class);

		verify(definitionRepository, never()).update(any(), eq(2L));
	}

	@Test
	void disablingDefinitionRequestsCancellationForUnstartedExecutions() {
		UUID queued = UUID.randomUUID();
		UUID dispatched = UUID.randomUUID();
		MBatchDefinitionsRecord current = definition(10L, 3L, true, "active");
		MBatchDefinitionsRecord updated = definition(10L, 4L, false, "active");
		when(definitionRepository.findById(10L))
				.thenReturn(java.util.Optional.of(current))
				.thenReturn(java.util.Optional.of(updated));
		when(definitionRepository.update(any(), eq(3L))).thenReturn(1);
		when(executionRequestRepository.findCancellableExecutionIdsByDefinition(10L))
				.thenReturn(List.of(queued, dispatched));

		BatchDefinitionDetailDto result = service.save(request(10L, 3L, false, "active"));

		assertThat(result.isEnabled()).isFalse();
		assertThat(result.getVersion()).isEqualTo(4L);
		verify(executionControlService).requestCancellationIfNotStarted(queued, "definition-disabled");
		verify(executionControlService).requestCancellationIfNotStarted(dispatched, "definition-disabled");
	}

	@Test
	void deletionRequiresDisabledDefinitionWithoutNonTerminalExecutions() {
		MBatchDefinitionsRecord current = definition(10L, 3L, false, "active");
		when(definitionRepository.findById(10L)).thenReturn(java.util.Optional.of(current));
		when(definitionRepository.hasNonTerminalExecutionRequests(10L)).thenReturn(true);

		BatchDefinitionRegistRequest exceptionInput2 = request(10L, 3L, false, "deleted");
		assertThatThrownBy(() -> service.save(exceptionInput2))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("non-terminal");

		verify(definitionRepository, never()).update(any(), eq(3L));
	}

	@Test
	void disabledDefinitionCanBeLogicallyDeleted() {
		MBatchDefinitionsRecord current = definition(10L, 3L, false, "active");
		MBatchDefinitionsRecord deleted = definition(10L, 4L, false, "deleted");
		when(definitionRepository.findById(10L))
				.thenReturn(java.util.Optional.of(current))
				.thenReturn(java.util.Optional.of(deleted));
		when(definitionRepository.update(any(), eq(3L))).thenReturn(1);

		BatchDefinitionDetailDto result = service.save(request(10L, 3L, false, "deleted"));

		assertThat(result.getStatus()).isEqualTo("deleted");
		assertThat(result.getVersion()).isEqualTo(4L);
	}

	private MBatchDefinitionsRecord definition(Long id, Long version, boolean enabled, String status) {
		MBatchDefinitionsRecord databaseRecord = new MBatchDefinitionsRecord();
		databaseRecord.setId(id);
		databaseRecord.setVersion(version);
		databaseRecord.setStatus(status);
		databaseRecord.setEnabled(enabled);
		databaseRecord.setBatchName("daily-job");
		databaseRecord.setViewName("Daily job");
		databaseRecord.setCategory("system");
		databaseRecord.setExecutionType("command");
		databaseRecord.setExecutionCommand("run.cmd");
		databaseRecord.setTimeoutSeconds(60);
		databaseRecord.setMaxRetryCount(0);
		databaseRecord.setPriority(5);
		databaseRecord.setAllowParallelExecution(false);
		databaseRecord.setLogRetentionDate(java.time.LocalDate.of(2999, 12, 31));
		databaseRecord.setLogPath("logs/daily.log");
		databaseRecord.setSuccessNotification("none");
		databaseRecord.setFailureNotification("none");
		return databaseRecord;
	}

	private BatchDefinitionRegistRequest request(Long id, Long version, boolean enabled, String status) {
		return BatchDefinitionRegistRequest.builder()
				.id(id).version(version).status(status).enabled(enabled)
				.batchName("daily-job").viewName("Daily job").category("system")
				.executionType("command").executionCommand("run.cmd")
				.timeoutSeconds(60).maxRetryCount(0).priority(5)
				.allowParallelExecution(false).logRetentionDate(java.time.LocalDate.of(2999, 12, 31))
				.logPath("logs/daily.log").successNotification("none").failureNotification("none")
				.build();
	}
}
