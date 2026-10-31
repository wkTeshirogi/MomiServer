package jp.xoki.web.zunda.smarthomewebserverbackend.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** バッチ共通設定DTO。設定値の時間単位は秒、保持期間は日。 */
@Getter
@Builder
public class BatchGlobalSettingDetailDto {

	@JsonProperty("setting_version")
	private Long settingVersion;
    @JsonProperty("attachment_max_file_mib")
    private Integer attachmentMaxFileMib;
    @JsonProperty("attachment_max_total_mib")
    private Integer attachmentMaxTotalMib;
    @JsonProperty("attachment_max_count")
    private Integer attachmentMaxCount;
    @JsonProperty("work_directory_retention_days")
    private Integer workDirectoryRetentionDays;

	@JsonProperty("max_concurrent_executions")
	private Integer maxConcurrentExecutions;
	@JsonProperty("default_timeout_seconds")
	private Integer defaultTimeoutSeconds;
	@JsonProperty("result_retry_interval_seconds")
	private Integer resultRetryIntervalSeconds;
	@JsonProperty("result_retry_count")
	private Integer resultRetryCount;
	@JsonProperty("unsent_result_retention_days")
	private Integer unsentResultRetentionDays;
	@JsonProperty("result_wait_timeout_seconds")
	private Integer resultWaitTimeoutSeconds;
	@JsonProperty("start_wait_timeout_seconds")
	private Integer startWaitTimeoutSeconds;
	@JsonProperty("cancel_grace_seconds")
	private Integer cancelGraceSeconds;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
}
