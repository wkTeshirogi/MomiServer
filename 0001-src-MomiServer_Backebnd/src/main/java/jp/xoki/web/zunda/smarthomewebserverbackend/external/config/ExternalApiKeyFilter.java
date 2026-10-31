package jp.xoki.web.zunda.smarthomewebserverbackend.external.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.SecurityErrorResponseHandler;
import jp.xoki.web.zunda.smarthomewebserverbackend.external.service.ExternalApiKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jp.xoki.web.zunda.smarthomewebserverbackend.common.config.SecuritySettingProperties;
import static jp.xoki.web.zunda.smarthomewebserverbackend.external.constants.ExternalApiKeyFilterConstants.HOUR_SECONDS;
import java.time.Clock;
import java.util.Optional;
import jp.xoki.web.zunda.smarthomewebserverbackend.external.dto.ExternalApiKeyDto;

/** 外部API専用認証。Gatewayの有効無効設定にかかわらず必須とする。 */
@Component
@RequiredArgsConstructor
public class ExternalApiKeyFilter extends OncePerRequestFilter {
	private final ExternalApiKeyService service;
	private final SecurityErrorResponseHandler errors;

	private final Clock clock;
	private final SecuritySettingProperties security;

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		return "OPTIONS".equals(request.getMethod()) || !path.matches("/api/v\\d+/external(?:/.*)?");
	}

	/** 専用キー認証とレート制限。
	 * @param request リクエスト
	 * @param response レスポンス
	 * @param chain 後続処理
	 * @throws ServletException 後続処理失敗
	 * @throws IOException 応答出力失敗
	 */
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		SecurityContextHolder.clearContext();
		request.setAttribute("gateway.clientType", "external");
		try {
			ExternalApiKeyDto key = service.authenticate(request.getHeader("X-API-Key"));
			ArrayList<SimpleGrantedAuthority> authorities = new ArrayList<SimpleGrantedAuthority>();
			authorities.add(new SimpleGrantedAuthority("ROLE_EXTERNAL"));
			key.scopes().forEach(scope -> authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope)));
			SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
					"external:" + key.id(), null, authorities));
			request.setAttribute("external.apiKeyId", key.id());
			Optional<Integer> used = service.consume(key);
			long reset = Instant.now(clock).truncatedTo(ChronoUnit.HOURS).getEpochSecond() + HOUR_SECONDS;
			int limit = security.getExternalInventoryHourlyLimit();
			response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
			response.setHeader("X-RateLimit-Reset", String.valueOf(reset));
			response.setHeader("X-RateLimit-Remaining", String.valueOf(
					used.map(count -> Math.max(0, limit - count)).orElse(0)));
			if (used.isEmpty()) {
				response.setHeader("Retry-After", String.valueOf(
						Math.max(1, reset - Instant.now(clock).getEpochSecond())));
				errors.write(request, response, HttpStatus.TOO_MANY_REQUESTS.value(),
						"Too Many Requests", "RATE_LIMIT_EXCEEDED",
						"API key request limit exceeded.");
				return;
			}
		} catch (BadCredentialsException _) {
			SecurityContextHolder.clearContext();
			errors.write(request, response, HttpStatus.UNAUTHORIZED.value(),
					"Unauthorized", "INVALID_API_KEY",
					"A valid API key is required.");
			return;
		} catch (DataAccessException _) {
			SecurityContextHolder.clearContext();
			errors.write(request, response, HttpStatus.SERVICE_UNAVAILABLE.value(),
					"Service Unavailable", "API_KEY_UNAVAILABLE",
					"API key verification is unavailable.");
			return;
		}
		chain.doFilter(request, response);
	}
}
