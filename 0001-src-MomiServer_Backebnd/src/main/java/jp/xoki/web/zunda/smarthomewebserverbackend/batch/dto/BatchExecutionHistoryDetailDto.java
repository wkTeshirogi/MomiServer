package jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** バッチ実行履歴の検索結果を返すためのDTO。 */
@Getter
@Builder
public class BatchExecutionHistoryDetailDto {
	private Long id;
	@JsonProperty("batch_definition_id")
	private Long batchDefinitionId;
	@JsonProperty("test_execution")
	private boolean testExecution;
	@JsonProperty("execution_started_dt")
	private LocalDateTime executionStartedDt;
	@JsonProperty("execution_finished_dt")
	private LocalDateTime executionFinishedDt;
	@JsonProperty("execution_result")
	private String executionResult;
	@JsonProperty("exit_code")
	private Integer exitCode;
	@JsonProperty("log_path")
	private String logPath;
	private String message;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
}
