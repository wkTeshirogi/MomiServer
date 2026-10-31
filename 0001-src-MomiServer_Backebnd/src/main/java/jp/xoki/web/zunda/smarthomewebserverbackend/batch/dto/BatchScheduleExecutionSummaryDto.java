package jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.model.BatchExecutionStatus;

import java.time.LocalDate;

/** カレンダー上のスケジュール実行状態を日付・時間帯別に集計した結果。 */
public record BatchScheduleExecutionSummaryDto(
		Long schedule_id,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
		LocalDate scheduled_date,
		Integer scheduled_hour,
		BatchExecutionStatus execution_status,
		Integer execution_count
) {
}
