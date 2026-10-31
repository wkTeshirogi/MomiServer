package jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.model.BatchExecutionStatus;
import lombok.Getter;
import lombok.Setter;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.constants.BatchMessageConstants;

/** Agentから送信されるバッチ実行完了通知のリクエストDTO。 */
@Getter
@Setter
public class AgentBatchCompleteRequest {

    @Size(max = 65536)
    private String stdout;
    @Size(max = 65536)
    private String stderr;
    @JsonProperty("output_truncated")
    private Boolean outputTruncated;

	/** timeout/cancelledにはプロセス停止確認、expiredには未開始確認が必要。 */
	@JsonProperty("stop_confirmed")
	private Boolean stopConfirmed;
	@JsonProperty("not_started")
	private Boolean notStarted;

	/** Agentが報告する最終実行状態。 */
	@NotNull(message = BatchMessageConstants.AGENT_BATCH_COMPLETE_EXECUTION_STATUS_REQUIRED)
	@JsonProperty("execution_status")
	private BatchExecutionStatus executionStatus;

	/** Agentプロセスの終了コード。 */
	@JsonProperty("exit_code")
	private Integer exitCode;

	/** Agent側または対象マシン上の実行ログパス。 */
	@Size(max = 500, message = BatchMessageConstants.AGENT_BATCH_COMPLETE_LOG_PATH_SIZE)
	@JsonProperty("log_path")
	private String logPath;

	/** Agentが返す実行結果メッセージ。 */
	private String message;
}
