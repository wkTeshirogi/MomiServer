package jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository;

import jp.xoki.web.zunda.jooq.tables.TBatchExecutionRequests;
import static jp.xoki.web.zunda.jooq.Tables.M_AGENTS;
import static jp.xoki.web.zunda.jooq.Tables.M_BATCH_DEFINITION_PARAMETERS;
import static org.jooq.impl.DSL.localDateTimeAdd;
import org.jooq.DatePart;

import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchExecutionRequestDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.model.BatchExecutionStatus;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Clock;
import java.util.LinkedHashMap;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.service.BatchAttachmentService;
import org.jooq.Condition;

/** バッチ実行要求テーブルへアクセスするRepository。 */
@Repository
@RequiredArgsConstructor
public class BatchExecutionRequestRepository {

	/** バッチ実行要求テーブル。 */
	private static final TBatchExecutionRequests T_BATCH_EXECUTION_REQUESTS = TBatchExecutionRequests.T_BATCH_EXECUTION_REQUESTS;

	/** DB内部ID。 */
	private static final Field<Long> ID = T_BATCH_EXECUTION_REQUESTS.ID;

	/** Agent連携で利用する外部参照用実行ID。 */
	private static final Field<UUID> EXECUTION_ID = T_BATCH_EXECUTION_REQUESTS.EXECUTION_ID;

	/** バッチ定義ID。 */
	private static final Field<Long> BATCH_DEFINITION_ID = T_BATCH_EXECUTION_REQUESTS.BATCH_DEFINITION_ID;

	/** バッチスケジュールID。 */
	private static final Field<Long> SCHEDULE_ID = T_BATCH_EXECUTION_REQUESTS.SCHEDULE_ID;

	/** 実行先Agent ID。 */
	private static final Field<Long> TARGET_AGENT_ID = T_BATCH_EXECUTION_REQUESTS.TARGET_AGENT_ID;

	/** 実行要求種別。 */
	private static final Field<String> REQUESTED_TYPE = T_BATCH_EXECUTION_REQUESTS.REQUESTED_TYPE;

	/** 実行要求者。 */
	private static final Field<String> REQUESTED_BY = T_BATCH_EXECUTION_REQUESTS.REQUESTED_BY;

	/** 実行状態。 */
	private static final Field<String> EXECUTION_STATUS = T_BATCH_EXECUTION_REQUESTS.EXECUTION_STATUS;

	/** テスト実行フラグ。 */
	private static final Field<Boolean> TEST_EXECUTION = T_BATCH_EXECUTION_REQUESTS.TEST_EXECUTION;

	/** Agentへ送信する実行コマンド。 */
	private static final Field<String> EXECUTION_COMMAND = T_BATCH_EXECUTION_REQUESTS.EXECUTION_COMMAND;

	/** コマンド実行時の作業ディレクトリ。 */
	private static final Field<String> WORKING_DIRECTORY = T_BATCH_EXECUTION_REQUESTS.WORKING_DIRECTORY;

	/** 実行タイムアウト秒数。 */
	private static final Field<Integer> TIMEOUT_SECONDS = T_BATCH_EXECUTION_REQUESTS.TIMEOUT_SECONDS;

	/** 最大リトライ回数。 */
	private static final Field<Integer> MAX_RETRY_COUNT = T_BATCH_EXECUTION_REQUESTS.MAX_RETRY_COUNT;

	/** 現在のリトライ回数。 */
	private static final Field<Integer> RETRY_COUNT = T_BATCH_EXECUTION_REQUESTS.RETRY_COUNT;

	/** Agent通知の試行回数。 */
	private static final Field<Integer> DISPATCH_ATTEMPT_COUNT = T_BATCH_EXECUTION_REQUESTS.DISPATCH_ATTEMPT_COUNT;

	/** 実行ログパス。 */
	private static final Field<String> LOG_PATH = T_BATCH_EXECUTION_REQUESTS.LOG_PATH;

	/** Agentから返却された終了コード。 */
	private static final Field<Integer> EXIT_CODE = T_BATCH_EXECUTION_REQUESTS.EXIT_CODE;

	/** 実行結果メッセージ。 */
	private static final Field<String> MESSAGE = T_BATCH_EXECUTION_REQUESTS.MESSAGE;

	/** スケジュール実行予定日時。 */
	private static final Field<LocalDateTime> SCHEDULED_FIRE_TIME = T_BATCH_EXECUTION_REQUESTS.SCHEDULED_FIRE_TIME;

	/** 実行要求日時。 */
	private static final Field<LocalDateTime> REQUESTED_AT = T_BATCH_EXECUTION_REQUESTS.REQUESTED_AT;

	/** Agentへ実行指示を送信した日時。 */
	private static final Field<LocalDateTime> DISPATCHED_AT = T_BATCH_EXECUTION_REQUESTS.DISPATCHED_AT;

	/** 最後にAgent通知を試行した日時。 */
	private static final Field<LocalDateTime> LAST_DISPATCH_ATTEMPT_AT = T_BATCH_EXECUTION_REQUESTS.LAST_DISPATCH_ATTEMPT_AT;

	/** Agentが開始通知を返した日時。 */
	private static final Field<LocalDateTime> STARTED_AT = T_BATCH_EXECUTION_REQUESTS.STARTED_AT;

	/** 実行完了日時。 */
	private static final Field<LocalDateTime> FINISHED_AT = T_BATCH_EXECUTION_REQUESTS.FINISHED_AT;

	/** 作成日時。 */
	private static final Field<LocalDateTime> CREATED_DT = T_BATCH_EXECUTION_REQUESTS.CREATED_DT;

	/** 更新日時。 */
	private static final Field<LocalDateTime> UPDATED_DT = T_BATCH_EXECUTION_REQUESTS.UPDATED_DT;

	private static final Field<Long> SETTING_VERSION = T_BATCH_EXECUTION_REQUESTS.SETTING_VERSION;
	private static final Field<Integer> MAX_CONCURRENT_EXECUTIONS = T_BATCH_EXECUTION_REQUESTS.MAX_CONCURRENT_EXECUTIONS;
	private static final Field<Integer> RESULT_RETRY_INTERVAL_SECONDS = T_BATCH_EXECUTION_REQUESTS.RESULT_RETRY_INTERVAL_SECONDS;
	private static final Field<Integer> RESULT_RETRY_COUNT = T_BATCH_EXECUTION_REQUESTS.RESULT_RETRY_COUNT;
	private static final Field<Integer> UNSENT_RESULT_RETENTION_DAYS = T_BATCH_EXECUTION_REQUESTS.UNSENT_RESULT_RETENTION_DAYS;
	private static final Field<Long> RESULT_WAIT_TIMEOUT_SECONDS = T_BATCH_EXECUTION_REQUESTS.RESULT_WAIT_TIMEOUT_SECONDS;
	private static final Field<Integer> CANCEL_GRACE_SECONDS = T_BATCH_EXECUTION_REQUESTS.CANCEL_GRACE_SECONDS;
	private static final Field<LocalDateTime> START_DEADLINE_AT = T_BATCH_EXECUTION_REQUESTS.START_DEADLINE_AT;
	private static final Field<LocalDateTime> START_RECEIVED_AT = T_BATCH_EXECUTION_REQUESTS.START_RECEIVED_AT;
	private static final Field<LocalDateTime> RESULT_DEADLINE_AT = T_BATCH_EXECUTION_REQUESTS.RESULT_DEADLINE_AT;
	private static final Field<LocalDateTime> CANCEL_REQUESTED_AT = T_BATCH_EXECUTION_REQUESTS.CANCEL_REQUESTED_AT;
	private static final Field<String> CANCEL_REQUESTED_BY = T_BATCH_EXECUTION_REQUESTS.CANCEL_REQUESTED_BY;
	private static final Field<LocalDateTime> LAST_CANCEL_ATTEMPT_AT = T_BATCH_EXECUTION_REQUESTS.LAST_CANCEL_ATTEMPT_AT;
	private static final Field<String> CONFIRMATION_STATUS = T_BATCH_EXECUTION_REQUESTS.CONFIRMATION_STATUS;

	/** jOOQ実行コンテキスト。 */
	private final DSLContext dsl;
    private final jp.xoki.web.zunda.smarthomewebserverbackend.batch.service.BatchAttachmentService attachments;
    private final jp.xoki.web.zunda.smarthomewebserverbackend.common.service.BatchGlobalSettingService settings;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;

	private final Clock clock;

	/**
	 * バッチ実行要求を作成する。
	 *
	 * @param request 作成するバッチ実行要求
	 * @return 作成されたDB内部ID
	 */
	@org.springframework.transaction.annotation.Transactional
    public Long create(BatchExecutionRequestDetailDto request) {
        List<BatchAttachmentService.Attachment> manifest = attachments.snapshot(request.getBatchDefinitionId());
        org.jooq.JSONB manifestJson;
        try { manifestJson = org.jooq.JSONB.valueOf(mapper.writeValueAsString(manifest)); }
        catch (Exception ex) { throw new IllegalStateException("Cannot snapshot attachments", ex); }
		LocalDateTime now = LocalDateTime.now(clock);
		return dsl.insertInto(T_BATCH_EXECUTION_REQUESTS)
				.set(BATCH_DEFINITION_ID, request.getBatchDefinitionId())
				.set(SCHEDULE_ID, request.getScheduleId())
				.set(TARGET_AGENT_ID, request.getTargetAgentId())
				.set(T_BATCH_EXECUTION_REQUESTS.TARGET_AGENT_GENERATION,
						dsl.select(M_AGENTS.GENERATION).from(M_AGENTS)
								.where(M_AGENTS.DEVICE_ID.eq(request.getTargetAgentId()))
								.and(M_AGENTS.STATUS.eq("registered")))
				.set(REQUESTED_TYPE, request.getRequestedType())
				.set(REQUESTED_BY, request.getRequestedBy())
				.set(EXECUTION_STATUS, request.getExecutionStatus().getStoredName())
				.set(TEST_EXECUTION, request.getTestExecution())
				.set(EXECUTION_COMMAND, request.getExecutionCommand())
                .set(T_BATCH_EXECUTION_REQUESTS.PARAMETERS, parameterSnapshot(request))
				.set(WORKING_DIRECTORY, request.getWorkingDirectory())
                .set(T_BATCH_EXECUTION_REQUESTS.ATTACHMENTS, manifestJson)
                .set(T_BATCH_EXECUTION_REQUESTS.WORK_DIRECTORY_RETENTION_DAYS, settings.detail().getWorkDirectoryRetentionDays())
				.set(TIMEOUT_SECONDS, request.getTimeoutSeconds())
				.set(MAX_RETRY_COUNT, request.getMaxRetryCount())
				.set(RETRY_COUNT, request.getRetryCount())
				.set(SCHEDULED_FIRE_TIME, request.getScheduledFireTime())
				.set(LOG_PATH, request.getLogPath())
				.set(REQUESTED_AT, request.getRequestedAt())
				.set(SETTING_VERSION, request.getSettingVersion())
				.set(MAX_CONCURRENT_EXECUTIONS, request.getMaxConcurrentExecutions())
				.set(RESULT_RETRY_INTERVAL_SECONDS, request.getResultRetryIntervalSeconds())
				.set(RESULT_RETRY_COUNT, request.getResultRetryCount())
				.set(UNSENT_RESULT_RETENTION_DAYS, request.getUnsentResultRetentionDays())
				.set(RESULT_WAIT_TIMEOUT_SECONDS, request.getResultWaitTimeoutSeconds())
				.set(CANCEL_GRACE_SECONDS, request.getCancelGraceSeconds())
				.set(START_DEADLINE_AT, request.getStartDeadlineAt())
				.set(CONFIRMATION_STATUS, request.getConfirmationStatus())
				.set(FINISHED_AT, request.getFinishedAt())
				.set(MESSAGE, request.getMessage())
				.set(CREATED_DT, now)
				.set(UPDATED_DT, now)
				.onConflictDoNothing()
				.returningResult(ID)
				.fetchOne(ID);
	}

	/**
	 * DB内部IDに一致するバッチ実行要求を取得する。
	 *
	 * @param id DB内部ID
	 * @return バッチ実行要求
	 */
	public Optional<BatchExecutionRequestDetailDto> findById(Long id) {
		return dsl.selectFrom(T_BATCH_EXECUTION_REQUESTS)
				.where(ID.eq(id))
				.fetchOptional(this::toDto);
	}

	/**
	 * 外部参照用実行IDに一致するバッチ実行要求を取得する。
	 *
	 * @param executionId 外部参照用実行ID
	 * @return バッチ実行要求
	 */
	public Optional<BatchExecutionRequestDetailDto> findByExecutionId(UUID executionId) {
		return dsl.selectFrom(T_BATCH_EXECUTION_REQUESTS)
				.where(EXECUTION_ID.eq(executionId))
				.fetchOptional(this::toDto);
	}

	public Optional<BatchExecutionRequestDetailDto> lockByExecutionId(UUID executionId) {
		return dsl.selectFrom(T_BATCH_EXECUTION_REQUESTS).where(EXECUTION_ID.eq(executionId))
				.forUpdate().fetchOptional(this::toDto);
	}

	/** 更新待機中に停止済みと断定できない要求数を取得する。
	 *
	 * 配信済み・実行中・旧形式の配信試行あり要求を含め、結果不明を自動で完了扱いにしない。
	 *
	 * @return 実行中または開始未確認の要求数
	 */
	public long countUnsettledExecutions() {
		return dsl.fetchCount(T_BATCH_EXECUTION_REQUESTS,
				EXECUTION_STATUS.in(BatchExecutionStatus.RUNNING.getStoredName(), BatchExecutionStatus.DISPATCHED.getStoredName())
						.or(EXECUTION_STATUS.eq(BatchExecutionStatus.QUEUED.getStoredName()).and(DISPATCH_ATTEMPT_COUNT.gt(0))));
	}

	/** 予定時刻を迎えた配信待ち要求の取得。
	 *
	 * 手動要求と予定時刻以降の要求を対象とし、キャンセル済み・期限切れ・再通知間隔内の要求を除外する。
	 *
	 * @param now 配信可否の判定日時
	 * @param limit 最大取得件数
	 * @return 配信対象の実行要求
	 */
	public List<BatchExecutionRequestDetailDto> findPendingDispatch(LocalDateTime now, int limit) {
		return dsl.selectFrom(T_BATCH_EXECUTION_REQUESTS)
				.where(EXECUTION_STATUS.in(BatchExecutionStatus.QUEUED.getStoredName(), BatchExecutionStatus.DISPATCHED.getStoredName()))
				.and(CANCEL_REQUESTED_AT.isNull()).and(START_DEADLINE_AT.gt(now))
				.and(SCHEDULED_FIRE_TIME.isNull().or(SCHEDULED_FIRE_TIME.le(now)))
				.and(LAST_DISPATCH_ATTEMPT_AT.isNull().or(LAST_DISPATCH_ATTEMPT_AT.le(now.minusSeconds(30))))
				.orderBy(LAST_DISPATCH_ATTEMPT_AT.asc().nullsFirst(), ID.asc()).limit(limit).fetch(this::toDto);
	}

	public List<BatchExecutionRequestDetailDto> findPendingCancellation(LocalDateTime now, int limit) {
		return dsl.selectFrom(T_BATCH_EXECUTION_REQUESTS)
				.where(EXECUTION_STATUS.in(BatchExecutionStatus.QUEUED.getStoredName(), BatchExecutionStatus.DISPATCHED.getStoredName(), BatchExecutionStatus.RUNNING.getStoredName()))
				.and(CANCEL_REQUESTED_AT.isNotNull())
				.and(LAST_CANCEL_ATTEMPT_AT.isNull().or(LAST_CANCEL_ATTEMPT_AT.le(now.minusSeconds(30))))
				.orderBy(LAST_CANCEL_ATTEMPT_AT.asc().nullsFirst(), ID.asc()).limit(limit).fetch(this::toDto);
	}

	/**
	 * 定義の無効化に伴う取消対象を取得する。
	 *
	 * 実行中の要求は定義の無効化だけでは停止しないため対象外とする。
	 *
	 * @param batchDefinitionId バッチ定義ID
	 * @return 配信待ちまたは配信済みの実行ID一覧
	 */
	public List<UUID> findCancellableExecutionIdsByDefinition(Long batchDefinitionId) {
		return dsl.select(EXECUTION_ID)
				.from(T_BATCH_EXECUTION_REQUESTS)
				.where(BATCH_DEFINITION_ID.eq(batchDefinitionId))
				.and(EXECUTION_STATUS.in(BatchExecutionStatus.QUEUED.getStoredName(), BatchExecutionStatus.DISPATCHED.getStoredName()))
				.and(CANCEL_REQUESTED_AT.isNull())
				.orderBy(ID.asc())
				.fetch(EXECUTION_ID);
	}

	public List<BatchExecutionRequestDetailDto> findMonitorCandidates(LocalDateTime now, int limit) {
		Condition startOverdue = EXECUTION_STATUS.in(BatchExecutionStatus.QUEUED.getStoredName(), BatchExecutionStatus.DISPATCHED.getStoredName()).and(START_DEADLINE_AT.le(now))
				.and(CONFIRMATION_STATUS.eq("pending")).and(CANCEL_REQUESTED_AT.isNull());
		Condition resultOverdue = EXECUTION_STATUS.eq(BatchExecutionStatus.RUNNING.getStoredName()).and(RESULT_DEADLINE_AT.le(now))
				.and(CONFIRMATION_STATUS.eq("pending")).and(CANCEL_REQUESTED_AT.isNull());
		Field<LocalDateTime> stopDeadline = localDateTimeAdd(CANCEL_REQUESTED_AT, CANCEL_GRACE_SECONDS, DatePart.SECOND);
		Condition stopOverdue = CANCEL_REQUESTED_AT.isNotNull().and(stopDeadline.le(now))
				.and(CONFIRMATION_STATUS.ne("stop_unconfirmed"));
		return dsl.selectFrom(T_BATCH_EXECUTION_REQUESTS)
				.where(EXECUTION_STATUS.in(BatchExecutionStatus.QUEUED.getStoredName(), BatchExecutionStatus.DISPATCHED.getStoredName(), BatchExecutionStatus.RUNNING.getStoredName()))
				.and(startOverdue.or(resultOverdue).or(stopOverdue))
				.orderBy(ID.asc()).limit(limit).fetch(this::toDto);
	}

	/** Caller holds the row lock in the same transaction as history/notification writes. */
	public void saveControlState(BatchExecutionRequestDetailDto request) {
		dsl.update(T_BATCH_EXECUTION_REQUESTS)
				.set(EXECUTION_STATUS, request.getExecutionStatus().getStoredName())
				.set(CONFIRMATION_STATUS, request.getConfirmationStatus())
				.set(STARTED_AT, request.getStartedAt())
				.set(START_RECEIVED_AT, request.getStartReceivedAt())
				.set(RESULT_DEADLINE_AT, request.getResultDeadlineAt())
				.set(DISPATCHED_AT, request.getDispatchedAt())
				.set(LAST_DISPATCH_ATTEMPT_AT, request.getLastDispatchAttemptAt())
				.set(DISPATCH_ATTEMPT_COUNT, request.getDispatchAttemptCount())
				.set(CANCEL_REQUESTED_AT, request.getCancelRequestedAt())
				.set(CANCEL_REQUESTED_BY, request.getCancelRequestedBy())
				.set(LAST_CANCEL_ATTEMPT_AT, request.getLastCancelAttemptAt())
				.set(EXIT_CODE, request.getExitCode())
				.set(LOG_PATH, request.getLogPath())
				.set(MESSAGE, request.getMessage())
				.set(FINISHED_AT, request.getFinishedAt())
				.set(UPDATED_DT, LocalDateTime.now(clock))
				.where(ID.eq(request.getId())).execute();
	}

	/**
	 * バッチ実行要求レコードをDTOへ変換する。
	 *
	 * @param record バッチ実行要求レコード
	 * @return バッチ実行要求DTO
	 */
	public void saveOutput(UUID executionId, String stdout, String stderr, boolean truncated) {
        dsl.update(T_BATCH_EXECUTION_REQUESTS).set(T_BATCH_EXECUTION_REQUESTS.STDOUT, stdout)
                .set(T_BATCH_EXECUTION_REQUESTS.STDERR, stderr).set(T_BATCH_EXECUTION_REQUESTS.OUTPUT_TRUNCATED, truncated)
                .where(EXECUTION_ID.eq(executionId)).execute();
    }

    private org.jooq.JSONB parameterSnapshot(BatchExecutionRequestDetailDto request) {
        LinkedHashMap<String, String> values = new java.util.LinkedHashMap<String, String>();
        LocalDateTime date = request.getScheduledFireTime() != null ? request.getScheduledFireTime() : request.getRequestedAt();
        for (Record row : dsl.selectFrom(M_BATCH_DEFINITION_PARAMETERS)
                .where(M_BATCH_DEFINITION_PARAMETERS.BATCH_DEFINITION_ID.eq(request.getBatchDefinitionId()))
                .and(M_BATCH_DEFINITION_PARAMETERS.ENABLED.isTrue())
                .orderBy(M_BATCH_DEFINITION_PARAMETERS.DISPLAY_ORDER, M_BATCH_DEFINITION_PARAMETERS.ID).fetch()) {
            values.put(row.get("parameter_name", String.class), resolveParameterValue(row, date));
        }
        try { return org.jooq.JSONB.valueOf(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(values)); }
        catch (com.fasterxml.jackson.core.JsonProcessingException _) { throw new IllegalArgumentException("Invalid parameters"); }
    }

    private String resolveParameterValue(Record row, java.time.LocalDateTime date) {
        String type = row.get("parameter_type", String.class);
        if ("fixed".equals(type)) {
            return row.get("fixed_value", String.class);
        }
        if (!isDynamicParameter(row, type)) {
            throw new IllegalArgumentException("Unsupported batch parameter type");
        }
        String format = row.get("value_format", String.class);
        if (format == null || format.isBlank()) {
            format = "dynamic_date".equals(type) ? "yyyy-MM-dd" : "HH:mm:ss";
        }
        return date.format(java.time.format.DateTimeFormatter.ofPattern(format));
    }

    private boolean isDynamicParameter(Record row, String type) {
        String dynamicValueType = row.get("dynamic_value_type", String.class);
        return ("dynamic_date".equals(type) && "current_date".equals(dynamicValueType))
                || ("dynamic_time".equals(type) && "current_time".equals(dynamicValueType));
    }

    private java.util.Map<String, String> parameters(Record databaseRecord) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(databaseRecord.get("parameters", String.class),
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String,String>>() { });
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException _) {
            throw new IllegalStateException("Invalid execution parameter snapshot");
        }
    }

	private BatchExecutionRequestDetailDto toDto(Record databaseRecord) {
		return BatchExecutionRequestDetailDto.builder()
				.id(databaseRecord.get(ID))
				.settingVersion(databaseRecord.get(SETTING_VERSION, Long.class))
				.maxConcurrentExecutions(databaseRecord.get(MAX_CONCURRENT_EXECUTIONS, Integer.class))
				.resultRetryIntervalSeconds(databaseRecord.get(RESULT_RETRY_INTERVAL_SECONDS, Integer.class))
				.resultRetryCount(databaseRecord.get(RESULT_RETRY_COUNT, Integer.class))
				.unsentResultRetentionDays(databaseRecord.get(UNSENT_RESULT_RETENTION_DAYS, Integer.class))
				.resultWaitTimeoutSeconds(databaseRecord.get(RESULT_WAIT_TIMEOUT_SECONDS, Long.class))
				.cancelGraceSeconds(databaseRecord.get(CANCEL_GRACE_SECONDS, Integer.class))
				.startDeadlineAt(databaseRecord.get(START_DEADLINE_AT, LocalDateTime.class))
				.startReceivedAt(databaseRecord.get(START_RECEIVED_AT, LocalDateTime.class))
				.resultDeadlineAt(databaseRecord.get(RESULT_DEADLINE_AT, LocalDateTime.class))
				.cancelRequestedAt(databaseRecord.get(CANCEL_REQUESTED_AT, LocalDateTime.class))
				.cancelRequestedBy(databaseRecord.get(CANCEL_REQUESTED_BY, String.class))
				.lastCancelAttemptAt(databaseRecord.get(LAST_CANCEL_ATTEMPT_AT, LocalDateTime.class))
				.confirmationStatus(databaseRecord.get(CONFIRMATION_STATUS, String.class))
				.executionId(databaseRecord.get(EXECUTION_ID))
                .workDirectoryRetentionDays(databaseRecord.get("work_directory_retention_days", Integer.class))
                .attachments(attachments.executionFiles(databaseRecord.get(EXECUTION_ID)))
				.batchDefinitionId(databaseRecord.get(BATCH_DEFINITION_ID))
				.scheduleId(databaseRecord.get(SCHEDULE_ID))
				.targetAgentId(databaseRecord.get(TARGET_AGENT_ID))
                .parameters(parameters(databaseRecord))
                .stdout(databaseRecord.get("stdout", String.class)).stderr(databaseRecord.get("stderr", String.class))
                .outputTruncated(databaseRecord.get("output_truncated", Boolean.class))
				.requestedType(databaseRecord.get(REQUESTED_TYPE))
				.requestedBy(databaseRecord.get(REQUESTED_BY))
				.executionStatus(BatchExecutionStatus.from(databaseRecord.get(EXECUTION_STATUS)))
				.testExecution(databaseRecord.get(TEST_EXECUTION))
				.executionCommand(databaseRecord.get(EXECUTION_COMMAND))
				.workingDirectory(databaseRecord.get(WORKING_DIRECTORY))
				.timeoutSeconds(databaseRecord.get(TIMEOUT_SECONDS))
				.maxRetryCount(databaseRecord.get(MAX_RETRY_COUNT))
				.retryCount(databaseRecord.get(RETRY_COUNT))
				.dispatchAttemptCount(databaseRecord.get(DISPATCH_ATTEMPT_COUNT))
				.logPath(databaseRecord.get(LOG_PATH))
				.exitCode(databaseRecord.get(EXIT_CODE))
				.message(databaseRecord.get(MESSAGE))
				.scheduledFireTime(databaseRecord.get(SCHEDULED_FIRE_TIME, LocalDateTime.class))
				.requestedAt(databaseRecord.get(REQUESTED_AT, LocalDateTime.class))
				.dispatchedAt(databaseRecord.get(DISPATCHED_AT, LocalDateTime.class))
				.lastDispatchAttemptAt(databaseRecord.get(LAST_DISPATCH_ATTEMPT_AT, LocalDateTime.class))
				.startedAt(databaseRecord.get(STARTED_AT, LocalDateTime.class))
				.finishedAt(databaseRecord.get(FINISHED_AT, LocalDateTime.class))
				.createdAt(databaseRecord.get(CREATED_DT, LocalDateTime.class))
				.updatedAt(databaseRecord.get(UPDATED_DT, LocalDateTime.class))
				.build();
	}
}
