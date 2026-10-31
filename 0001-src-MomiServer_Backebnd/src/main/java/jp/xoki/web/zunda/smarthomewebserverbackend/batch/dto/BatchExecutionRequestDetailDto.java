package jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.model.BatchExecutionStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/** バッチ実行要求の詳細情報を返却するDTO。 */
@Getter
@Builder(toBuilder = true)
public class BatchExecutionRequestDetailDto {
    @JsonProperty("work_directory_retention_days")
    private Integer workDirectoryRetentionDays;
    private java.util.List<jp.xoki.web.zunda.smarthomewebserverbackend.batch.service.BatchAttachmentService.Attachment> attachments;
    private String stdout;
    private String stderr;
    @JsonProperty("output_truncated")
    private Boolean outputTruncated;
    private java.util.Map<String, String> parameters;

	@JsonProperty("setting_version")
	private Long settingVersion;
	@JsonProperty("max_concurrent_executions")
	private Integer maxConcurrentExecutions;
	@JsonProperty("result_retry_interval_seconds")
	private Integer resultRetryIntervalSeconds;
	@JsonProperty("result_retry_count")
	private Integer resultRetryCount;
	@JsonProperty("unsent_result_retention_days")
	private Integer unsentResultRetentionDays;
	@JsonProperty("result_wait_timeout_seconds")
	private Long resultWaitTimeoutSeconds;
	@JsonProperty("cancel_grace_seconds")
	private Integer cancelGraceSeconds;
	@JsonProperty("start_deadline_at")
	private LocalDateTime startDeadlineAt;
	@JsonProperty("start_received_at")
	private LocalDateTime startReceivedAt;
	@JsonProperty("result_deadline_at")
	private LocalDateTime resultDeadlineAt;
	@JsonProperty("cancel_requested_at")
	private LocalDateTime cancelRequestedAt;
	@JsonProperty("cancel_requested_by")
	private String cancelRequestedBy;
	@JsonProperty("last_cancel_attempt_at")
	private LocalDateTime lastCancelAttemptAt;
	@JsonProperty("confirmation_status")
	private String confirmationStatus;

	/** DB内部ID。 */
	private Long id;

	/** Agent連携で利用する外部参照用実行ID。 */
    @JsonProperty("execution_id")
    private UUID executionId;

	/** 実行対象のバッチ定義ID。 */
    @JsonProperty("batch_definition_id")
    private Long batchDefinitionId;

	/** スケジュール実行時に紐づくバッチスケジュールID。 */
    @JsonProperty("schedule_id")
    private Long scheduleId;

	/** 実行先Agent ID。 */
    @JsonProperty("target_agent_id")
    private Long targetAgentId;

	/** 手動実行またはスケジュール実行を表す要求種別。 */
    @JsonProperty("requested_type")
    private String requestedType;

	/** 実行要求者。 */
    @JsonProperty("requested_by")
    private String requestedBy;

	/** 現在の実行状態。 */
    @JsonProperty("execution_status")
    private BatchExecutionStatus executionStatus;

	/** テスト実行フラグ。 */
    @JsonProperty("test_execution")
    private Boolean testExecution;

	/** Agentへ送信する実行コマンド。 */
    @JsonProperty("execution_command")
    private String executionCommand;

	/** コマンド実行時の作業ディレクトリ。 */
    @JsonProperty("working_directory")
    private String workingDirectory;

	/** 実行タイムアウト秒数。 */
    @JsonProperty("timeout_seconds")
    private Integer timeoutSeconds;

	/** 最大リトライ回数。 */
    @JsonProperty("max_retry_count")
    private Integer maxRetryCount;

	/** 現在のリトライ回数。 */
    @JsonProperty("retry_count")
    private Integer retryCount;

	/** Agent通知の試行回数。 */
    @JsonProperty("dispatch_attempt_count")
    private Integer dispatchAttemptCount;

	/** 実行ログパス。 */
    @JsonProperty("log_path")
    private String logPath;

	/** Agentから返却された終了コード。 */
    @JsonProperty("exit_code")
    private Integer exitCode;

	/** 実行結果メッセージ。 */
	private String message;

	/** スケジュール実行予定日時。 */
    @JsonProperty("scheduled_fire_time")
    private LocalDateTime scheduledFireTime;

	/** 実行要求日時。 */
    @JsonProperty("requested_at")
    private LocalDateTime requestedAt;

	/** Agentへ実行指示を送信した日時。 */
    @JsonProperty("dispatched_at")
    private LocalDateTime dispatchedAt;

	/** 最後にAgent通知を試行した日時。 */
    @JsonProperty("last_dispatch_attempt_at")
    private LocalDateTime lastDispatchAttemptAt;

	/** Agentが開始通知を返した日時。 */
    @JsonProperty("started_at")
    private LocalDateTime startedAt;

	/** 実行完了日時。 */
    @JsonProperty("finished_at")
    private LocalDateTime finishedAt;

	/** 作成日時。 */
	private LocalDateTime createdAt;

	/** 更新日時。 */
	private LocalDateTime updatedAt;
}
