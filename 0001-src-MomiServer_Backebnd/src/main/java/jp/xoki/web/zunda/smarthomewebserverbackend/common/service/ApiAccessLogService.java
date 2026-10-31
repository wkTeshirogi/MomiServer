package jp.xoki.web.zunda.smarthomewebserverbackend.common.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.config.ApiAccessLogProperties;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.ApiAccessLogDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.ApiAccessLogPageDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.ApiAccessLogSearchRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.ApiAccessLogWriteRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.ResourceNotFoundException;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.repository.ApiAccessLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.ApiAccessLogServiceConstants.MAX_LIMIT;
import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.ApiAccessLogServiceConstants.MAX_RANGE_DAYS;
import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.ApiAccessLogServiceConstants.METHODS;
import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.ApiAccessLogServiceConstants.SOURCE_TYPES;
import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.ApiAccessLogServiceConstants.STATUS_CATEGORIES;
import java.time.Clock;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.pagination.Pagination;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.pagination.PageSlice;
import java.util.List;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.CommonMessageConstants;

/** APIアクセスログの登録・検索・保持期間管理Service。 */
@Service
@RequiredArgsConstructor
public class ApiAccessLogService {


	private final ApiAccessLogRepository repository;
	private final ApiAccessLogProperties properties;

	private final Clock clock;

	/** APIアクセスログ登録。
	 *
	 * API処理終了時にフィルターから渡されたアクセス情報を保存する。
	 *
	 * @param request 登録するアクセスログ
	 */
	public void write(ApiAccessLogWriteRequest request) {
		repository.insert(request);
	}

	/** APIアクセスログ一覧検索。
	 *
	 * 未指定の期間・ページサイズを補完し、最大31日間の範囲で検索する。
	 *
	 * @param request 画面から指定された検索条件
	 * @return アクセスログページ
	 */
	public ApiAccessLogPageDto search(ApiAccessLogSearchRequest request) {
		OffsetDateTime to = request.to() == null ? OffsetDateTime.now(clock) : request.to();
		OffsetDateTime from = request.from() == null ? to.minusHours(24) : request.from();
		if (from.isAfter(to)) {
			throw new IllegalArgumentException(CommonMessageConstants.LOG_PERIOD_REVERSED);
		}
		if (Duration.between(from, to).compareTo(Duration.ofDays(MAX_RANGE_DAYS)) > 0) {
			throw new IllegalArgumentException(CommonMessageConstants.LOG_PERIOD_EXCEEDED);
		}
		Pagination.requireOffset(request.offset(), CommonMessageConstants.OFFSET_INVALID);
		int limit = Pagination.requireLimit(request.limit(), MAX_LIMIT, CommonMessageConstants.LOG_LIMIT_INVALID);
		String method = normalizeUpper(request.http_method());
		String statusCategory = normalizeLower(request.status_category());
		String sourceType = normalizeLower(request.source_type());
		validateAllowed(CommonMessageConstants.HTTP_METHOD_LABEL, method, METHODS);
		validateAllowed(CommonMessageConstants.STATUS_CATEGORY_LABEL, statusCategory, STATUS_CATEGORIES);
		validateAllowed(CommonMessageConstants.SOURCE_TYPE_LABEL, sourceType, SOURCE_TYPES);
		if (request.status_code() != null
				&& (request.status_code() < 100 || request.status_code() > 599)) {
			throw new IllegalArgumentException(CommonMessageConstants.HTTP_STATUS_INVALID);
		}
		if (request.min_duration_ms() != null && request.min_duration_ms() < 0) {
			throw new IllegalArgumentException(CommonMessageConstants.MINIMUM_DURATION_INVALID);
		}

		ApiAccessLogSearchRequest normalized = new ApiAccessLogSearchRequest(
				from, to, method, trimToNull(request.request_path()), statusCategory,
				request.status_code(), sourceType, trimToNull(request.principal()),
				trimToNull(request.ip_address()), request.min_duration_ms(), request.offset(), limit);
		List<ApiAccessLogDetailDto> rows = repository.search(normalized);
		long totalCount = repository.count(normalized);
		PageSlice<ApiAccessLogDetailDto> page = PageSlice.fromRows(rows, limit, totalCount);
		return new ApiAccessLogPageDto(page.items(), page.hasMore(), page.totalCount());
	}

	/** APIアクセスログ詳細取得。
	 *
	 * @param requestId リクエストID
	 * @return APIアクセスログ詳細
	 */
	public ApiAccessLogDetailDto detail(UUID requestId) {
		return repository.findByRequestId(requestId)
				.orElseThrow(() -> new ResourceNotFoundException(CommonMessageConstants.API_ACCESS_LOG_RESOURCE, requestId));
	}

	/** 期限切れAPIアクセスログ削除。
	 *
	 * 設定された保持日数を過ぎたアクセスログを削除する。
	 *
	 * @param now 削除基準日時
	 * @return 削除件数
	 */
	public int cleanupExpired(OffsetDateTime now) {
		int retentionDays = Math.max(1, properties.getRetentionDays());
		return repository.deleteOccurredBefore(now.minusDays(retentionDays));
	}

	private void validateAllowed(String label, String value, Set<String> allowed) {
		if (value != null && !allowed.contains(value)) {
			throw new IllegalArgumentException(CommonMessageConstants.FIELD_INVALID.formatted(label));
		}
	}

	private String normalizeUpper(String value) {
		String normalized = trimToNull(value);
		return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
	}

	private String normalizeLower(String value) {
		String normalized = trimToNull(value);
		return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
	}

	private String trimToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
