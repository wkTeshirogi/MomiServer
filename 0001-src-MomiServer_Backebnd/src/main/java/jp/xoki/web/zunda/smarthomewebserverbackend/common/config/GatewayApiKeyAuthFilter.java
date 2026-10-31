package jp.xoki.web.zunda.smarthomewebserverbackend.common.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.SecurityErrorResponseHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.GatewayApiKeyAuthFilterConstants.EXTERNAL_API_PATH;

/**
 * Agent APIおよびIoT API向けのGateway制御を行うフィルター。
 *
 * <p>{@code /api/v{version}/agent/{path}} と {@code /api/v{version}/iot/{path}} を外部連携APIとして扱い、
 * IoTにはGateway設定に応じてAPIキー認証とレート制限を適用する。
 * Agentの認証・認可は専用の経路で扱い、このフィルターではAPIキーを検証しない。
 * 対象外のAPIは後続フィルターへそのまま委譲する。</p>
 */
@Component
@RequiredArgsConstructor
public class GatewayApiKeyAuthFilter extends OncePerRequestFilter {


	/** Gatewayの有効状態、APIキー、レート制限などの設定値。 */
	private final GatewayProperties gatewayProperties;

	/** Gateway対象APIのレート制限を判定するコンポーネント。 */
	private final GatewayRateLimiter gatewayRateLimiter;

	/** Gateway認証失敗およびレート制限超過時のJSONエラー応答を書き込むハンドラー。 */
	private final SecurityErrorResponseHandler securityErrorResponseHandler;

	/**
	 * Gateway対象APIにAPIキー認証とレート制限を適用する。
	 *
	 * <p>Gateway全体が無効な場合は制御を行わない。APIキー認証が無効で
	 * レート制限のみ有効な場合は、送信元IPを制限キーとして扱う。</p>
	 *
	 * @param request HTTPリクエスト
	 * @param response HTTPレスポンス
	 * @param filterChain 後続フィルターチェーン
	 * @throws ServletException フィルター処理中にServlet例外が発生した場合
	 * @throws IOException レスポンス書き込みまたは後続処理でI/O例外が発生した場合
	 */
	@Override
	protected void doFilterInternal(
			@NonNull HttpServletRequest request,
			@NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain
	) throws ServletException, IOException {
		String path = request.getServletPath();
		if (path == null || path.isEmpty()) {
			path = request.getRequestURI().substring(request.getContextPath().length());
		}
		Matcher matcher = EXTERNAL_API_PATH.matcher(path);

		// Gateway対象外のAPIは認証方式やレート制限を変更せず後続へ委譲する。
		if (!matcher.matches()) {
			filterChain.doFilter(request, response);
			return;
		}

		request.setAttribute("gateway.apiVersion", matcher.group(1));
		String clientType = matcher.group(2);
		request.setAttribute("gateway.clientType", clientType);

		// Agentは専用認証を使用する。APIキーを端末認証として扱わない。
		// AgentAuthFilterとSecurityConfigが専用認証を担当する。
		if ("agent".equals(clientType)) {
			filterChain.doFilter(request, response);
			return;
		}

		// Gateway全体が無効な場合はAPIキー認証もレート制限も適用しない。
		if (!gatewayProperties.isEnabled()) {
			filterChain.doFilter(request, response);
			return;
		}

		// APIキー認証のみ無効な場合は、送信元IPを単位としてレート制限を適用する。
		if (!gatewayProperties.getApiKey().isEnabled()) {

			// IP単位のレート制限を超過した場合は、後続処理へ進めず429を返す。
			if (!consumeRateLimit(request, response, clientType, null)) {
				return;
			}
			filterChain.doFilter(request, response);
			return;
		}

		String apiKey = request.getHeader(gatewayProperties.getApiKey().getHeaderName());
		List<String> allowedKeys = gatewayProperties.getApiKey().getIotKeys();

		// APIキー認証が有効な場合、未指定または未登録のキーはGateway入口で拒否する。
		if (apiKey == null || apiKey.isBlank() || allowedKeys.stream().noneMatch(apiKey::equals)) {
			securityErrorResponseHandler.write(
					request,
					response,
					401,
					"Unauthorized",
					"INVALID_GATEWAY_API_KEY",
					"Gateway API key is required."
			);
			return;
		}

		// APIキー認証後、認証済みAPIキーを単位としてレート制限を適用する。
		if (!consumeRateLimit(request, response, clientType, apiKey)) {
			return;
		}

		filterChain.doFilter(request, response);
	}

	/**
	 * Gatewayレート制限を消費し、超過時は429レスポンスを書き込む。
	 *
	 * @param request HTTPリクエスト
	 * @param response HTTPレスポンス
	 * @param clientType Gatewayクライアント種別
	 * @param apiKey 認証済みAPIキー。APIキー認証無効時はnull
	 * @return 後続処理を続行してよい場合はtrue
	 * @throws IOException レスポンス書き込みに失敗した場合
	 */
	private boolean consumeRateLimit(
			HttpServletRequest request,
			HttpServletResponse response,
			String clientType,
			String apiKey
	) throws IOException {

		// レート制限が無効な環境では、リクエストをそのまま許可する。
		if (!gatewayProperties.getRateLimit().isEnabled()) {
			return true;
		}

		GatewayRateLimiter.RateLimitResult result = gatewayRateLimiter.consume(
				rateLimitKey(request, clientType, apiKey));
		response.setHeader("X-RateLimit-Limit", String.valueOf(result.limit()));
		response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));

		// 制限枠が残っている場合は、後続のSpring Securityフィルターへ処理を継続する。
		if (result.allowed()) {
			return true;
		}

		response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));
		securityErrorResponseHandler.write(
				request,
				response,
				429,
				"Too Many Requests",
				"GATEWAY_RATE_LIMIT_EXCEEDED",
				"Gateway API rate limit exceeded."
		);
		return false;
	}

	/**
	 * レート制限で使用する制限キーを生成する。
	 *
	 * <p>APIキーがある場合はAPIキー単位、APIキー認証が無効な場合は送信元IP単位で
	 * クライアント種別ごとに独立した制限キーを生成する。</p>
	 *
	 * @param request HTTPリクエスト
	 * @param clientType Gatewayクライアント種別
	 * @param apiKey 認証済みAPIキー。APIキー認証無効時はnull
	 * @return レート制限キー
	 */
	private String rateLimitKey(HttpServletRequest request, String clientType, String apiKey) {

		// APIキー認証が有効な場合はAPIキー、無効な場合は送信元IPを主体として扱う。
		String principal = apiKey != null && !apiKey.isBlank()
				? apiKey
				: request.getRemoteAddr();
		return clientType + ":" + principal;
	}

}
