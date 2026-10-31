package jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository;

import jp.xoki.web.zunda.smarthomewebserverbackend.batch.model.BatchExecutionStatus;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchScheduleExecutionLogDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchScheduleExecutionSummaryDto;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record10;
import org.jooq.Record5;
import org.jooq.Result;

class BatchScheduleExecutionRepositoryTest {
	@Test
	void loadsOnlyCompletedLogsForTheSelectedHour() throws Exception {
		DSLContext dsl = DSL.using(SQLDialect.POSTGRES);
		Field<UUID> executionId = DSL.field("execution_id", UUID.class);
		Field<LocalDateTime> fireTime = DSL.field("scheduled_fire_time", LocalDateTime.class);
		Field<String> status = DSL.field("execution_status", String.class);
		Field<LocalDateTime> started = DSL.field("started_at", LocalDateTime.class);
		Field<LocalDateTime> finished = DSL.field("finished_at", LocalDateTime.class);
		Field<Integer> exitCode = DSL.field("exit_code", Integer.class);
		Field<String> message = DSL.field("message", String.class);
		Field<String> stdout = DSL.field("stdout", String.class);
		Field<String> stderr = DSL.field("stderr", String.class);
		Field<Boolean> truncated = DSL.field("output_truncated", Boolean.class);
		Result<Record10<UUID, LocalDateTime, String, LocalDateTime, LocalDateTime, Integer, String, String, String, Boolean>> result = dsl.newResult(executionId, fireTime, status, started, finished,
				exitCode, message, stdout, stderr, truncated);
		Record10<UUID, LocalDateTime, String, LocalDateTime, LocalDateTime, Integer, String, String, String, Boolean> row = dsl.newRecord(executionId, fireTime, status, started, finished,
				exitCode, message, stdout, stderr, truncated);
		UUID id = UUID.randomUUID();
		row.fromArray(id, LocalDateTime.of(2026, 9, 27, 1, 30), "failed", null,
				LocalDateTime.of(2026, 9, 27, 1, 32), 1, "failure", "out", "err", false);
		result.add(row);
		AtomicReference<String> sql = new AtomicReference<String>();
		AtomicReference<List<Object>> bindings = new AtomicReference<List<Object>>();

		try (MockConnection connection = new MockConnection(ctx -> {
			sql.set(ctx.sql().toLowerCase());
			bindings.set(Arrays.asList(ctx.bindings()));
			return new MockResult[]{new MockResult(1, result)};
		})) {
			List<BatchScheduleExecutionLogDto> logs = new BatchScheduleExecutionRepository(DSL.using(connection, SQLDialect.POSTGRES))
					.findCompletedLogs(3L, LocalDate.of(2026, 9, 27), 1, 50, 51);
			assertThat(logs).hasSize(1);
			assertThat(logs.getFirst().execution_id()).isEqualTo(id);
			assertThat(logs.getFirst().stderr()).isEqualTo("err");
		}

		assertThat(sql.get()).contains("schedule_id", "execution_status", "fetch next", "offset");
		assertThat(bindings.get()).contains(3L, "success", "failed", "timeout",
				Timestamp.valueOf(LocalDateTime.of(2026, 9, 27, 1, 0)),
				Timestamp.valueOf(LocalDateTime.of(2026, 9, 27, 2, 0)));
	}

	@Test
	void summarizesScheduledRequestsWithinTheRequestedDates() throws Exception {
		DSLContext dsl = DSL.using(SQLDialect.POSTGRES);
		Field<Long> scheduleId = DSL.field("schedule_id", Long.class);
		Field<LocalDate> date = DSL.field("scheduled_date", LocalDate.class);
		Field<Integer> hour = DSL.field("scheduled_hour", Integer.class);
		Field<String> status = DSL.field("execution_status", String.class);
		Field<Integer> count = DSL.field("execution_count", Integer.class);
		Result<Record5<Long, LocalDate, Integer, String, Integer>> result = dsl.newResult(scheduleId, date, hour, status, count);
		Record5<Long, LocalDate, Integer, String, Integer> row = dsl.newRecord(scheduleId, date, hour, status, count);
		row.fromArray(3L, LocalDate.of(2026, 9, 27), 1, "success", 2);
		result.add(row);
		AtomicReference<String> sql = new AtomicReference<String>();
		AtomicReference<List<Object>> bindings = new AtomicReference<List<Object>>();

		try (MockConnection connection = new MockConnection(ctx -> {
			sql.set(ctx.sql().toLowerCase());
			bindings.set(Arrays.asList(ctx.bindings()));
			return new MockResult[]{new MockResult(1, result)};
		})) {
			BatchScheduleExecutionRepository repository = new BatchScheduleExecutionRepository(DSL.using(connection, SQLDialect.POSTGRES));
			List<BatchScheduleExecutionSummaryDto> summaries = repository.summarize(LocalDate.of(2026, 9, 27), LocalDate.of(2026, 9, 27));
			assertThat(summaries).hasSize(1);
			assertThat(summaries.getFirst().schedule_id()).isEqualTo(3L);
			assertThat(summaries.getFirst().execution_status()).isEqualTo(BatchExecutionStatus.SUCCESS);
			assertThat(summaries.getFirst().execution_count()).isEqualTo(2);
		}

		assertThat(sql.get()).contains("schedule_id", "scheduled_fire_time", "group by");
		assertThat(bindings.get()).contains(
				Timestamp.valueOf(LocalDateTime.of(2026, 9, 27, 0, 0)),
				Timestamp.valueOf(LocalDateTime.of(2026, 9, 28, 0, 0)));
	}
}
