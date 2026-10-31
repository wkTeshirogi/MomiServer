package jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.constants.BatchMessageConstants;

/** 手動バッチ実行要求の作成時に受け取るリクエストDTO。 */
@Getter
@Setter
public class BatchExecutionRequestCreateRequest {

	/** 実行先Agent ID。 */
	@NotNull(message = BatchMessageConstants.BATCH_EXECUTION_REQUEST_CREATE_TARGET_AGENT_ID_REQUIRED)
	@JsonProperty("target_agent_id")
	private Long targetAgentId;

	/** テスト実行として扱うかどうか。 */
	@JsonProperty("test_execution")
	private Boolean testExecution;
}
