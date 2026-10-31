package jp.xoki.web.zunda.smarthomewebserverbackend.common.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.ApiAccessLogWriteRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.ApiAccessLogAttributes;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.service.ApiAccessLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.ApiAccessLogFilterConstants.MAX_ENDPOINT_LENGTH;
import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.ApiAccessLogFilterConstants.MAX_ERROR_CODE_LENGTH;
import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.ApiAccessLogFilterConstants.MAX_IP_LENGTH;
import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.ApiAccessLogFilterConstants.MAX_PATH_LENGTH;
import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.ApiAccessLogFilterConstants.MAX_PRINCIPAL_LENGTH;
import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.ApiAccessLogFilterConstants.MAX_USER_AGENT_LENGTH;
import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.ApiAccessLogFilterConstants.SOURCE_TYPE_AGENT;
import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.ApiAccessLogFilterConstants.VERSIONED_SOURCE_PATH;
import java.time.Clock;
import java.util.regex.Matcher;

/** HTTP APIアクセスを機密情報を除外してDBへ記録するフィルター。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApiAccessLogFilter extends OncePerRequestFilter {


	private final ApiAccessLogService service;
	private final ApiAccessLogProperties properties;

	private final Clock clock;

	/** API処理とアクセスログ記録。
	 *
	 * 後続フィルター・Controllerの完了後にステータスと処理時間を保存する。
	 * ログ保存失敗は本来のAPIレスポンスへ影響させない。
	 *
	 * @param request HTTPリクエスト
	 * @param response HTTPレスポンス
	 * @param filterChain 後続フィルターチェーン
	 * @throws ServletException 後続処理でServlet例外が発生した場合
	 * @throws IOException 後続処理で入出力例外が発生した場合
	 */
	@Override
	protected void doFilterInternal(
			@NonNull HttpServletRequest request,
			@NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain
	) throws ServletException, IOException {
		UUID requestId = UUID.randomUUID();
		OffsetDateTime occurredAt = OffsetDateTime.now(clock);
		long startedAt = System.nanoTime();
		boolean completed = false;
		request.setAttribute(ApiAccessLogAttributes.REQUEST_ID, requestId);
		response.setHeader("X-Request-ID", requestId.toString());

		try {
			filterChain.doFilter(request, response);
			completed = true;
		} finally {
			int status = completed || response.getStatus() >= 400
					? response.getStatus() : HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
			long durationMs = Math.max(0L,
					TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
			try {
				service.write(new ApiAccessLogWriteRequest(
						requestId,
						occurredAt,
						request.getMethod().toUpperCase(Locale.ROOT),
						truncate(request.getRequestURI(), MAX_PATH_LENGTH),
						truncate(attribute(request, HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE),
								MAX_ENDPOINT_LENGTH),
						status,
						durationMs,
						resolveSourceType(request),
						truncate(resolvePrincipal(), MAX_PRINCIPAL_LENGTH),
						truncate(request.getRemoteAddr(), MAX_IP_LENGTH),
						truncate(request.getHeader("User-Agent"), MAX_USER_AGENT_LENGTH),
						truncate(attribute(request, ApiAccessLogAttributes.ERROR_CODE),
								MAX_ERROR_CODE_LENGTH)));
			} catch (RuntimeException exception) {
				log.warn("API access log persistence failed. requestId={}, method={}, path={}",
						requestId, request.getMethod(), request.getRequestURI(), exception);
			}
		}
	}

	@Override
	protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
		return !properties.isEnabled()
				|| "OPTIONS".equalsIgnoreCase(request.getMethod())
				|| !request.getRequestURI().startsWith(request.getContextPath() + "/api/");
	}

	private String resolveSourceType(HttpServletRequest request) {
		if (hasApiSourcePath(request.getRequestURI(), "external")) {
			return "gateway";
		}
		String gatewayType = attribute(request, "gateway.clientType");
		if (SOURCE_TYPE_AGENT.equals(gatewayType) || "iot".equals(gatewayType)) {
			return gatewayType;
		}
		String path = request.getRequestURI();
		if (hasApiSourcePath(path, SOURCE_TYPE_AGENT)) {
			return SOURCE_TYPE_AGENT;
		}
		if (hasApiSourcePath(path, "iot")) {
			return "iot";
		}
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return isAuthenticatedPrincipal(authentication) ? "web" : "anonymous";
	}

	private boolean hasApiSourcePath(String path, String source) {
		Matcher matcher = VERSIONED_SOURCE_PATH.matcher(path);
		while (matcher.find()) {
			if (source.equals(matcher.group(1))) {
				return true;
			}
		}
		return false;
	}

	private String resolvePrincipal() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (!isAuthenticatedPrincipal(authentication)) {
			return null;
		}
		return authentication.getName();
	}

	private boolean isAuthenticatedPrincipal(Authentication authentication) {
		return authentication != null && authentication.isAuthenticated()
				&& !"anonymousUser".equals(authentication.getPrincipal());
	}

	private String attribute(HttpServletRequest request, String name) {
		Object value = request.getAttribute(name);
		return value == null ? null : value.toString();
	}

	private String truncate(String value, int maxLength) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.length() <= maxLength ? value : value.substring(0, maxLength);
	}
}
