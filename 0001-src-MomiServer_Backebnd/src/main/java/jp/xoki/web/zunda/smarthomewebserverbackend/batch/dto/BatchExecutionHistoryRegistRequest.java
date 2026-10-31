package jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.constants.BatchMessageConstants;

/** バッチ実行履歴の登録または更新で受け取るリクエストDTO。 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchExecutionHistoryRegistRequest {
	private Long id;

	@NotNull(message = BatchMessageConstants.BATCH_EXECUTION_HISTORY_BATCH_DEFINITION_ID_REQUIRED)
	@JsonProperty("batch_definition_id")
	private Long batchDefinitionId;

	@NotNull(message = BatchMessageConstants.BATCH_EXECUTION_HISTORY_TEST_EXECUTION_REQUIRED)
	@JsonProperty("test_execution")
	private Boolean testExecution;

	@NotNull(message = BatchMessageConstants.BATCH_EXECUTION_HISTORY_EXECUTION_STARTED_DT_REQUIRED)
	@JsonProperty("execution_started_dt")
	private LocalDateTime executionStartedDt;

	@JsonProperty("execution_finished_dt")
	private LocalDateTime executionFinishedDt;

	@NotBlank(message = BatchMessageConstants.BATCH_EXECUTION_HISTORY_EXECUTION_RESULT_REQUIRED)
	@Size(max = 50, message = BatchMessageConstants.BATCH_EXECUTION_HISTORY_EXECUTION_RESULT_SIZE)
	@JsonProperty("execution_result")
	private String executionResult;

	@JsonProperty("exit_code")
	private Integer exitCode;

	@Size(max = 500, message = BatchMessageConstants.BATCH_EXECUTION_HISTORY_LOG_PATH_SIZE)
	@JsonProperty("log_path")
	private String logPath;

	private String message;
}
