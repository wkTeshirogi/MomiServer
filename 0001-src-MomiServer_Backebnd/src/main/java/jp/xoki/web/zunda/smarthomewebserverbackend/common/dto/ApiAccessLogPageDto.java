package jp.xoki.web.zunda.smarthomewebserverbackend.common.dto;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/** APIアクセスログ一覧のページDTO。 */
public record ApiAccessLogPageDto(List<ApiAccessLogDetailDto> logs, boolean has_more, long total_count) {
	public ApiAccessLogPageDto {
		logs = logs == null ? null : Collections.unmodifiableList(new ArrayList<>(logs));
	}
}
