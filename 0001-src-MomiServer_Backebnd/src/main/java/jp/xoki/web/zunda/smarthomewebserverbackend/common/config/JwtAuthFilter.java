package jp.xoki.web.zunda.smarthomewebserverbackend.common.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jp.xoki.web.zunda.smarthomewebserverbackend.auth.service.AuthSessionService;
import jp.xoki.web.zunda.smarthomewebserverbackend.auth.service.JwtService;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.RoleEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import io.jsonwebtoken.JwtException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/** AuthorizationヘッダーのBearerトークンを検証し、認証情報をSecurityContextへ設定するフィルター。 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

	private final JwtService jwtService;
	private final AuthSessionService authSessionService;
	private final UserDetailsService userDetailsService;

	/**
	 * リクエストからJWTを取り出し、有効な場合のみ認証済みユーザーとして設定する。
	 *
	 * @param request HTTPリクエスト
	 * @param response HTTPレスポンス
	 * @param filterChain 後続フィルターチェーン
	 * @throws ServletException フィルター処理に失敗した場合
	 * @throws IOException 入出力に失敗した場合
	 */
	@Override
	protected void doFilterInternal(
			@NonNull HttpServletRequest request,
			@NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain
	) throws ServletException, IOException {

		final String authHeader = request.getHeader("Authorization");

		// Authorization ヘッダーがない or Bearer でない場合はスキップ
		if (authHeader == null || !authHeader.startsWith("Bearer ")) {
			filterChain.doFilter(request, response);
			return;
		}

		final String token = authHeader.substring(7);

		// トークンからusername(email)を取り出す
		final String username;
		final String sessionId;
		try {
			username = jwtService.extractUsername(token);
			sessionId = jwtService.extractSessionId(token);
		} catch (JwtException | IllegalArgumentException _) {
			SecurityContextHolder.clearContext();
			filterChain.doFilter(request, response);
			return;
		}

		// 未認証の場合のみ処理
		if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
			UserDetails userDetails;
			try {
				userDetails = userDetailsService.loadUserByUsername(username);
			} catch (UsernameNotFoundException _) {
				filterChain.doFilter(request, response);
				return;
			}

			// BAN_USERへ変更されたユーザーは既存セッションも失効し、未認証として扱う。
			if (userDetails.getAuthorities().stream()
					.anyMatch(authority -> ("ROLE_" + RoleEnum.BAN_USER_ROLE.getStoredName())
							.equals(authority.getAuthority()))) {
				authSessionService.revoke(sessionId);
				SecurityContextHolder.clearContext();
				filterChain.doFilter(request, response);
				return;
			}

			if (jwtService.isValidAccessToken(token, userDetails)
					&& authSessionService.isActive(sessionId, username)) {
				UsernamePasswordAuthenticationToken authToken =
						new UsernamePasswordAuthenticationToken(
								userDetails,
								null,
								userDetails.getAuthorities()
						);
				SecurityContextHolder.getContext().setAuthentication(authToken);
			}
		}

		filterChain.doFilter(request, response);
	}
}
