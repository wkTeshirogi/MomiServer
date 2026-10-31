package jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.model.BatchExecutionStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/** カレンダー上の実行済み要求に紐づくログ。 */
public record BatchScheduleExecutionLogDto(
		UUID execution_id,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
		LocalDateTime scheduled_fire_time,
		BatchExecutionStatus execution_status,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
		LocalDateTime started_at,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
		LocalDateTime finished_at,
		Integer exit_code,
		String message,
		String stdout,
		String stderr,
		Boolean output_truncated) {
}
