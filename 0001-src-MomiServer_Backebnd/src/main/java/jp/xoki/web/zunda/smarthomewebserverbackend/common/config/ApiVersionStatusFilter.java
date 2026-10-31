package jp.xoki.web.zunda.smarthomewebserverbackend.common.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Matcher;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.SecurityErrorResponseHandler;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.repository.ApiVersionStatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.ApiVersionStatusFilterConstants.EXTERNAL_API_PATH;
import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.ApiVersionStatusFilterConstants.SUPPORTED_VERSION;

/** Agent・IoT APIの公開バージョンを認証処理より前に判定するフィルター。 */
@Component
@RequiredArgsConstructor
public class ApiVersionStatusFilter extends OncePerRequestFilter {


	private final ApiVersionStatusRepository repository;
	private final SecurityErrorResponseHandler errors;

	/**
	 * 実装済みバージョンとDB上の公開状態を検証する。
	 *
	 * @param request HTTPリクエスト
	 * @param response HTTPレスポンス
	 * @param chain 後続フィルターチェーン
	 * @throws ServletException 後続処理でServlet例外が発生した場合
	 * @throws IOException レスポンス書き込みまたは後続処理でI/O例外が発生した場合
	 */
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain chain) throws ServletException, IOException {
		String path = request.getServletPath();
		if (path == null || path.isEmpty()) {
			path = request.getRequestURI().substring(request.getContextPath().length());
		}
		Matcher matcher = EXTERNAL_API_PATH.matcher(path);
		if (!matcher.matches()) {
			chain.doFilter(request, response);
			return;
		}

		String version = matcher.group(1);
		if (!SUPPORTED_VERSION.equals(version)) {
			writeNotFound(request, response);
			return;
		}

		ApiVersionStatusRepository.ApiVersionStatus state;
		try {
			state = repository.find(matcher.group(2), version).orElse(null);
		} catch (DataAccessException _) {
			errors.write(request, response, 503, "Service Unavailable", "API_VERSION_UNAVAILABLE",
					"API version status is unavailable.");
			return;
		}
		if (state == null) {
			writeNotFound(request, response);
			return;
		}

		switch (state.status()) {
			case "active" -> chain.doFilter(request, response);
			case "deprecated" -> {
				response.setHeader("Deprecation", "@" + state.deprecatedEpochSeconds());
				chain.doFilter(request, response);
			}
			case "retired" -> errors.write(request, response, 410, "Gone", "API_VERSION_RETIRED",
					"This API version has been retired.");
			default -> errors.write(request, response, 503, "Service Unavailable", "API_VERSION_UNAVAILABLE",
					"API version status is unavailable.");
		}
	}

	private void writeNotFound(HttpServletRequest request, HttpServletResponse response) throws IOException {
		errors.write(request, response, 404, "Not Found", "API_VERSION_NOT_FOUND",
				"This API version is not available.");
	}
}
