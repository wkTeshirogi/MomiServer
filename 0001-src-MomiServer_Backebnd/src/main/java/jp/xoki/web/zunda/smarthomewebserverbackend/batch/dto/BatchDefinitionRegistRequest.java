package jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.constants.BatchMessageConstants;

/** バッチ定義マスタの登録または更新で受け取るリクエストDTO。 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchDefinitionRegistRequest {
	private Long id;
	private Long version;

	@Size(max = 20, message = BatchMessageConstants.BATCH_DEFINITION_STATUS_SIZE)
	private String status;

	@NotBlank(message = BatchMessageConstants.BATCH_DEFINITION_BATCH_NAME_REQUIRED)
	@Size(max = 255, message = BatchMessageConstants.BATCH_DEFINITION_BATCH_NAME_SIZE)
	@JsonProperty("batch_name")
	private String batchName;

	@NotBlank(message = BatchMessageConstants.BATCH_DEFINITION_VIEW_NAME_REQUIRED)
	@Size(max = 255, message = BatchMessageConstants.BATCH_DEFINITION_VIEW_NAME_SIZE)
	@JsonProperty("view_name")
	private String viewName;

	private String description;

	@NotNull(message = BatchMessageConstants.BATCH_DEFINITION_ENABLED_REQUIRED)
	private Boolean enabled;

	@NotBlank(message = BatchMessageConstants.BATCH_DEFINITION_CATEGORY_REQUIRED)
	@Size(max = 50, message = BatchMessageConstants.BATCH_DEFINITION_CATEGORY_SIZE)
	private String category;

	@Size(max = 500, message = BatchMessageConstants.BATCH_DEFINITION_TAGS_SIZE)
	private String tags;

	@NotBlank(message = BatchMessageConstants.BATCH_DEFINITION_EXECUTION_TYPE_REQUIRED)
	@Size(max = 50, message = BatchMessageConstants.BATCH_DEFINITION_EXECUTION_TYPE_SIZE)
	@JsonProperty("execution_type")
	private String executionType;

	@NotBlank
	@Size(max = 1000)
	@JsonProperty("execution_command")
	private String executionCommand;

	@Size(max = 500, message = BatchMessageConstants.BATCH_DEFINITION_WORKING_DIRECTORY_SIZE)
	@JsonProperty("working_directory")
	private String workingDirectory;

	@NotNull(message = BatchMessageConstants.BATCH_DEFINITION_TIMEOUT_SECONDS_REQUIRED)
	@Min(value = 0, message = BatchMessageConstants.BATCH_DEFINITION_TIMEOUT_SECONDS_MIN)
	@JsonProperty("timeout_seconds")
	private Integer timeoutSeconds;

	@NotNull(message = BatchMessageConstants.BATCH_DEFINITION_MAX_RETRY_COUNT_REQUIRED)
	@Min(value = 0, message = BatchMessageConstants.BATCH_DEFINITION_MAX_RETRY_COUNT_MIN)
	@Max(value = 9, message = BatchMessageConstants.BATCH_DEFINITION_MAX_RETRY_COUNT_MAX)
	@JsonProperty("max_retry_count")
	private Integer maxRetryCount;

	@Min(value = 0, message = BatchMessageConstants.BATCH_DEFINITION_RETRY_INTERVAL_SECONDS_MIN)
	@JsonProperty("retry_interval_seconds")
	private Integer retryIntervalSeconds;

	@Size(max = 255, message = BatchMessageConstants.BATCH_DEFINITION_EXECUTION_USER_SIZE)
	@JsonProperty("execution_user")
	private String executionUser;

	@Size(max = 50, message = BatchMessageConstants.BATCH_DEFINITION_REQUIRED_ROLE_SIZE)
	@JsonProperty("required_role")
	private String requiredRole;

	@NotNull(message = BatchMessageConstants.BATCH_DEFINITION_PRIORITY_REQUIRED)
	@Min(value = 1, message = BatchMessageConstants.BATCH_DEFINITION_PRIORITY_MIN)
	@Max(value = 9, message = BatchMessageConstants.BATCH_DEFINITION_PRIORITY_MAX)
	private Integer priority;

	@NotNull(message = BatchMessageConstants.BATCH_DEFINITION_ALLOW_PARALLEL_EXECUTION_REQUIRED)
	@JsonProperty("allow_parallel_execution")
	private Boolean allowParallelExecution;

	@NotNull(message = BatchMessageConstants.BATCH_DEFINITION_LOG_RETENTION_DATE_REQUIRED)
	@JsonProperty("log_retention_date")
	private LocalDate logRetentionDate;

	@NotBlank(message = BatchMessageConstants.BATCH_DEFINITION_LOG_PATH_REQUIRED)
	@Size(max = 500, message = BatchMessageConstants.BATCH_DEFINITION_LOG_PATH_SIZE)
	@JsonProperty("log_path")
	private String logPath;

	@NotBlank(message = BatchMessageConstants.BATCH_DEFINITION_SUCCESS_NOTIFICATION_REQUIRED)
	@Size(max = 50, message = BatchMessageConstants.BATCH_DEFINITION_SUCCESS_NOTIFICATION_SIZE)
	@JsonProperty("success_notification")
	private String successNotification;

	@NotBlank(message = BatchMessageConstants.BATCH_DEFINITION_FAILURE_NOTIFICATION_REQUIRED)
	@Size(max = 50, message = BatchMessageConstants.BATCH_DEFINITION_FAILURE_NOTIFICATION_SIZE)
	@JsonProperty("failure_notification")
	private String failureNotification;
}
