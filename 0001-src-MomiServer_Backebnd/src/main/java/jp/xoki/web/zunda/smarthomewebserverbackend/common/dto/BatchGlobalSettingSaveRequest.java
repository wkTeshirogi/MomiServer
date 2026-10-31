package jp.xoki.web.zunda.smarthomewebserverbackend.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.CommonMessageConstants;

/** バッチ共通設定の保存リクエスト。時間のAPI単位は秒。 */
public record BatchGlobalSettingSaveRequest(
		@NotNull @Min(1) Integer max_concurrent_executions,
		@NotNull @Min(1) Integer default_timeout_seconds,
		@NotNull @Min(1) Integer result_retry_interval_seconds,
		@NotNull @Min(0) Integer result_retry_count,
		@NotNull @Min(1) Integer unsent_result_retention_days,
		@NotNull @Min(1) Integer result_wait_timeout_seconds,
		@NotNull @Min(1) Integer start_wait_timeout_seconds,
		@NotNull @Min(1) Integer cancel_grace_seconds,
        @Min(1) @jakarta.validation.constraints.Max(10240) Integer attachment_max_file_mib,
        @Min(1) @jakarta.validation.constraints.Max(102400) Integer attachment_max_total_mib,
        @Min(1) @jakarta.validation.constraints.Max(1000) Integer attachment_max_count,
        @Min(1) @jakarta.validation.constraints.Max(3650) Integer work_directory_retention_days
) {
    public BatchGlobalSettingSaveRequest(Integer a, Integer b, Integer c, Integer d,
            Integer e, Integer f, Integer g, Integer h) {
        this(a,b,c,d,e,f,g,h,null,null,null,null);
    }

	@JsonIgnore
	@AssertTrue(message = CommonMessageConstants.BATCH_GLOBAL_SETTING_IS_RESULT_WAIT_VALID_VALID)
	public boolean isResultWaitValid() {
		return result_wait_timeout_seconds == null || default_timeout_seconds == null
				|| result_wait_timeout_seconds > default_timeout_seconds;
	}
}
