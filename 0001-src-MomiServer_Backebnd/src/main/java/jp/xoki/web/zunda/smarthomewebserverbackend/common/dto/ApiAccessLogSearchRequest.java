package jp.xoki.web.zunda.smarthomewebserverbackend.common.dto;

import java.time.OffsetDateTime;

/** APIアクセスログ検索条件。 */
public record ApiAccessLogSearchRequest(
		OffsetDateTime from,
		OffsetDateTime to,
		String http_method,
		String request_path,
		String status_category,
		Integer status_code,
		String source_type,
		String principal,
		String ip_address,
		Long min_duration_ms,
		int offset,
		int limit
) {
}
