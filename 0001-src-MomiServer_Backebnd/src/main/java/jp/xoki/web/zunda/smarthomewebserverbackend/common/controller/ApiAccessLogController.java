package jp.xoki.web.zunda.smarthomewebserverbackend.common.controller;

import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.ApiAccessLogDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.ApiAccessLogPageDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.ApiAccessLogSearchRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.security.annotation.CanOperate;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.service.ApiAccessLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

/** APIアクセスログの検索・詳細取得Controller。 */
@RestController
@RequestMapping("/api/logs/access")
@RequiredArgsConstructor
@CanOperate
public class ApiAccessLogController {

	private final ApiAccessLogService service;

	/** APIアクセスログ一覧取得。
	 *
	 * 指定条件に一致するアクセスログを発生日時の新しい順で返す。
	 *
	 * @return APIアクセスログページ
	 */
	@GetMapping
	public ResponseEntity<ApiAccessLogPageDto> list(
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
			@RequestParam(required = false, name = "http_method") String httpMethod,
			@RequestParam(required = false, name = "request_path") String requestPath,
			@RequestParam(required = false, name = "status_category") String statusCategory,
			@RequestParam(required = false, name = "status_code") Integer statusCode,
			@RequestParam(required = false, name = "source_type") String sourceType,
			@RequestParam(required = false) String principal,
			@RequestParam(required = false, name = "ip_address") String ipAddress,
			@RequestParam(required = false, name = "min_duration_ms") Long minDurationMs,
			@RequestParam(defaultValue = "0") int offset,
			@RequestParam(defaultValue = "50") int limit
	) {
		return ResponseEntity.ok(service.search(new ApiAccessLogSearchRequest(
				from, to, httpMethod, requestPath, statusCategory, statusCode,
				sourceType, principal, ipAddress, minDurationMs, offset, limit)));
	}

	/** APIアクセスログ詳細取得。
	 *
	 * @param requestId リクエストID
	 * @return APIアクセスログ詳細
	 */
	@GetMapping("/{requestId}")
	public ResponseEntity<ApiAccessLogDetailDto> detail(@PathVariable UUID requestId) {
		return ResponseEntity.ok(service.detail(requestId));
	}
}
