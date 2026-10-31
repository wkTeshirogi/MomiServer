package jp.xoki.web.zunda.smarthomewebserverbackend.common.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** APIアクセスログのDB登録値。 */
public record ApiAccessLogWriteRequest(
		UUID request_id,
		OffsetDateTime occurred_at,
		String http_method,
		String request_path,
		String endpoint_pattern,
		int status_code,
		long duration_ms,
		String source_type,
		String principal,
		String ip_address,
		String user_agent,
		String error_code
) {
}
