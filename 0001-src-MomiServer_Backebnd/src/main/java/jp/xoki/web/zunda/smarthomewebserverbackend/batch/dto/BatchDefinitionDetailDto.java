package jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** バッチ定義マスタの検索結果を返すためのDTO。 */
@Getter
@Builder
public class BatchDefinitionDetailDto {
	private Long id;
	private Long version;
	private String status;
	@JsonProperty("batch_name")
	private String batchName;
	@JsonProperty("view_name")
	private String viewName;
	private String description;
	private boolean enabled;
	private String category;
	private String tags;
	@JsonProperty("execution_type")
	private String executionType;
	@JsonProperty("execution_command")
	private String executionCommand;
	@JsonProperty("working_directory")
	private String workingDirectory;
	@JsonProperty("timeout_seconds")
	private int timeoutSeconds;
	@JsonProperty("max_retry_count")
	private int maxRetryCount;
	@JsonProperty("retry_interval_seconds")
	private Integer retryIntervalSeconds;
	@JsonProperty("execution_user")
	private String executionUser;
	@JsonProperty("required_role")
	private String requiredRole;
	private int priority;
	@JsonProperty("allow_parallel_execution")
	private boolean allowParallelExecution;
	@JsonProperty("log_retention_date")
	private LocalDate logRetentionDate;
	@JsonProperty("log_path")
	private String logPath;
	@JsonProperty("success_notification")
	private String successNotification;
	@JsonProperty("failure_notification")
	private String failureNotification;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
}
