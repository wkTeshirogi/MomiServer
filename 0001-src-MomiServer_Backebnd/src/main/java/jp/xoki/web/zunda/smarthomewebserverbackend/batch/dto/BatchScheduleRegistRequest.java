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

/** バッチスケジュールマスタの登録または更新で受け取るリクエストDTO。 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchScheduleRegistRequest {
	private Long id;

	@NotNull(message = BatchMessageConstants.BATCH_SCHEDULE_BATCH_DEFINITION_ID_REQUIRED)
	@JsonProperty("batch_definition_id")
	private Long batchDefinitionId;

	@NotNull(message = BatchMessageConstants.BATCH_SCHEDULE_TARGET_AGENT_ID_REQUIRED)
	@JsonProperty("target_agent_id")
	private Long targetAgentId;

	@NotBlank(message = BatchMessageConstants.BATCH_SCHEDULE_CRON_EXPRESSION_REQUIRED)
	@Size(max = 255, message = BatchMessageConstants.BATCH_SCHEDULE_CRON_EXPRESSION_SIZE)
	@JsonProperty("cron_expression")
	private String cronExpression;

	@JsonProperty("start_date")
	private LocalDate startDate;
	@JsonProperty("end_date")
	private LocalDate endDate;

	@NotNull(message = BatchMessageConstants.BATCH_SCHEDULE_ALLOW_HOLIDAY_EXECUTION_REQUIRED)
	@JsonProperty("allow_holiday_execution")
	private Boolean allowHolidayExecution;

	@NotNull(message = BatchMessageConstants.BATCH_SCHEDULE_ENABLED_REQUIRED)
	private Boolean enabled;

	@NotNull(message = BatchMessageConstants.BATCH_SCHEDULE_PRIORITY_REQUIRED)
	@Min(value = 1, message = BatchMessageConstants.BATCH_SCHEDULE_PRIORITY_MIN)
	@Max(value = 9, message = BatchMessageConstants.BATCH_SCHEDULE_PRIORITY_MAX)
	private Integer priority;
}
