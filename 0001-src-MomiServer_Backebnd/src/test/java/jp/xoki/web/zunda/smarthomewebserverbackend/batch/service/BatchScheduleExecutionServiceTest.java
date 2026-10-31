package jp.xoki.web.zunda.smarthomewebserverbackend.batch.service;

import jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository.BatchScheduleExecutionRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchScheduleExecutionLogDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository.BatchScheduleRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchScheduleExecutionLogPageDto;

class BatchScheduleExecutionServiceTest {

	private final BatchScheduleExecutionRepository executions = mock(BatchScheduleExecutionRepository.class);
	private final BatchScheduleService service = new BatchScheduleService(
			mock(BatchScheduleRepository.class), executions);

	@Test
	void acceptsAFullThirtyOneDayMonth() {
		LocalDate from = LocalDate.of(2026, 8, 1);
		LocalDate to = LocalDate.of(2026, 8, 31);
		when(executions.summarize(from, to)).thenReturn(List.of());

		assertThat(service.getExecutionSummaries(from, to)).isEmpty();
		verify(executions).summarize(from, to);
	}

	@Test
	void rejectsLongOrReversedRangesBeforeQuerying() {
		LocalDate from = LocalDate.of(2026, 8, 1);
		LocalDate exceptionInput1 = from.plusDays(31);
		assertThatThrownBy(() -> service.getExecutionSummaries(from, exceptionInput1))
				.isInstanceOf(IllegalArgumentException.class);
		LocalDate exceptionInput2 = from.minusDays(1);
		assertThatThrownBy(() -> service.getExecutionSummaries(from, exceptionInput2))
				.isInstanceOf(IllegalArgumentException.class);
		verifyNoInteractions(executions);
	}

	@Test
	void pagesCompletedLogsAndValidatesScope() {
		LocalDate date = LocalDate.of(2026, 9, 27);
		List<BatchScheduleExecutionLogDto> rows = java.util.Collections.nCopies(21, mock(BatchScheduleExecutionLogDto.class));
		when(executions.findCompletedLogs(3L, date, null, 0, 21)).thenReturn(rows);
		when(executions.countCompletedLogs(3L, date, null)).thenReturn(25L);
		BatchScheduleExecutionLogPageDto page = service.getExecutionLogs(3L, date, null, 0, 20);
		assertThat(page.logs()).hasSize(20);
		assertThat(page.has_more()).isTrue();
		assertThat(page.total_count()).isEqualTo(25L);
		assertThatThrownBy(() -> service.getExecutionLogs(3L, date, 24, 0, 20))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.getExecutionLogs(0L, date, null, 0, 20))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.getExecutionLogs(3L, date, null, 0, 25))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
