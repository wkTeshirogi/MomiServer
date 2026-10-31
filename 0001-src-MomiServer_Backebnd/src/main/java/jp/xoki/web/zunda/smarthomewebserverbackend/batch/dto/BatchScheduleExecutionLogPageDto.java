package jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/** 実行ログのページ。 */
public record BatchScheduleExecutionLogPageDto(
		List<BatchScheduleExecutionLogDto> logs, boolean has_more, long total_count) {
	public BatchScheduleExecutionLogPageDto {
		logs = logs == null ? null : Collections.unmodifiableList(new ArrayList<>(logs));
	}
}
