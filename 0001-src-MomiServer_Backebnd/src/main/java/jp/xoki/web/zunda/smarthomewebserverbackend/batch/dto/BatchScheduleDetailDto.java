package jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** バッチスケジュールマスタの検索結果を返すためのDTO。 */
@Getter
@Builder
public class BatchScheduleDetailDto {
	private Long id;
	@JsonProperty("batch_definition_id")
	private Long batchDefinitionId;
	@JsonProperty("target_agent_id")
	private Long targetAgentId;
	@JsonProperty("cron_expression")
	private String cronExpression;
	@JsonProperty("start_date")
	private LocalDate startDate;
	@JsonProperty("end_date")
	private LocalDate endDate;
	@JsonProperty("allow_holiday_execution")
	private boolean allowHolidayExecution;
	private boolean enabled;
	private int priority;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
}
