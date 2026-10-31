package jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository;

import jp.xoki.web.zunda.jooq.tables.TBatchExecutionRequests;

import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchScheduleExecutionSummaryDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchScheduleExecutionLogDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.model.BatchExecutionStatus;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Condition;
import org.jooq.DatePart;
import org.jooq.Field;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.jooq.impl.DSL.cast;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.extract;

/** カレンダー表示期間内のスケジュール実行要求を集計するRepository。 */
@Repository
@RequiredArgsConstructor
public class BatchScheduleExecutionRepository {

	private static final TBatchExecutionRequests EXECUTIONS = TBatchExecutionRequests.T_BATCH_EXECUTION_REQUESTS;
	private static final Field<Long> SCHEDULE_ID = EXECUTIONS.SCHEDULE_ID;
	private static final Field<LocalDateTime> FIRE_TIME = EXECUTIONS.SCHEDULED_FIRE_TIME;
	private static final Field<String> STATUS = EXECUTIONS.EXECUTION_STATUS;
	private static final Field<Long> ID = EXECUTIONS.ID;
	private static final Field<UUID> EXECUTION_ID = EXECUTIONS.EXECUTION_ID;
	private static final Field<LocalDateTime> STARTED_AT = EXECUTIONS.STARTED_AT;
	private static final Field<LocalDateTime> FINISHED_AT = EXECUTIONS.FINISHED_AT;
	private static final Field<Integer> EXIT_CODE = EXECUTIONS.EXIT_CODE;
	private static final Field<String> MESSAGE = EXECUTIONS.MESSAGE;
	private static final Field<String> STDOUT = EXECUTIONS.STDOUT;
	private static final Field<String> STDERR = EXECUTIONS.STDERR;
	private static final Field<Boolean> OUTPUT_TRUNCATED = EXECUTIONS.OUTPUT_TRUNCATED;

	private final DSLContext dsl;

	/** 表示期間内の要求をスケジュール・日付・時間帯・状態別に集計する。 */
	public List<BatchScheduleExecutionSummaryDto> summarize(LocalDate from, LocalDate to) {
		Field<LocalDate> date = cast(FIRE_TIME, LocalDate.class).as("scheduled_date");
		Field<Integer> hour = extract(FIRE_TIME, DatePart.HOUR).as("scheduled_hour");
		Field<Integer> total = count().as("execution_count");

		return dsl.select(SCHEDULE_ID, date, hour, STATUS, total)
				.from(EXECUTIONS)
				.where(SCHEDULE_ID.isNotNull())
				.and(FIRE_TIME.ge(from.atStartOfDay()))
				.and(FIRE_TIME.lt(to.plusDays(1).atStartOfDay()))
				.groupBy(SCHEDULE_ID, date, hour, STATUS)
				.fetch(row -> new BatchScheduleExecutionSummaryDto(
						row.get(SCHEDULE_ID), row.get(date), row.get(hour),
						BatchExecutionStatus.from(row.get(STATUS)), row.get(total)));
	}

	/** 指定した予定日の完了済み実行ログを新しい順に取得する。 */
	public List<BatchScheduleExecutionLogDto> findCompletedLogs(
			long scheduleId, LocalDate date, Integer hour, int offset, int limit) {
		Condition scope = completedLogScope(scheduleId, date, hour);
		return dsl.select(EXECUTION_ID, FIRE_TIME, STATUS, STARTED_AT, FINISHED_AT,
				EXIT_CODE, MESSAGE, STDOUT, STDERR, OUTPUT_TRUNCATED)
				.from(EXECUTIONS).where(scope)
				.orderBy(FIRE_TIME.desc(), ID.desc()).limit(limit).offset(offset)
				.fetch(row -> new BatchScheduleExecutionLogDto(
						row.get(EXECUTION_ID), row.get(FIRE_TIME), BatchExecutionStatus.from(row.get(STATUS)),
						row.get(STARTED_AT), row.get(FINISHED_AT), row.get(EXIT_CODE), row.get(MESSAGE),
						row.get(STDOUT), row.get(STDERR), row.get(OUTPUT_TRUNCATED)));
	}

	/** 指定した予定日の完了済み実行ログ総件数を取得する。 */
	public long countCompletedLogs(long scheduleId, LocalDate date, Integer hour) {
		return dsl.fetchCount(EXECUTIONS, completedLogScope(scheduleId, date, hour));
	}

	private Condition completedLogScope(long scheduleId, LocalDate date, Integer hour) {
		LocalDateTime from = date.atStartOfDay();
		LocalDateTime to = date.plusDays(1).atStartOfDay();
		if (hour != null) {
			from = date.atTime(hour, 0);
			to = from.plusHours(1);
		}
		return SCHEDULE_ID.eq(scheduleId).and(FIRE_TIME.ge(from)).and(FIRE_TIME.lt(to))
				.and(STATUS.in("success", "failed", "timeout"));
	}
}
