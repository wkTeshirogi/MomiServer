package jp.xoki.web.zunda.smarthomewebserverbackend.common.config;

import jp.xoki.web.zunda.smarthomewebserverbackend.agent.config.AgentAuthFilter;
import jp.xoki.web.zunda.smarthomewebserverbackend.external.config.ExternalApiKeyFilter;
import lombok.RequiredArgsConstructor;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.SecurityErrorResponseHandler;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.RoleEnum.ADMIN_ROLE;
import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.RoleEnum.OPERATOR_ROLE;
import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.RoleEnum.USER_ROLE;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.CommonMessageConstants;

/** Spring Security、JWT認証、CORS、認証失敗時レスポンスを設定するクラス。 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final AgentAuthFilter agentAuthFilter;

	@Bean
	public FilterRegistrationBean<AgentAuthFilter>
			agentAuthFilterRegistration() {
		FilterRegistrationBean<AgentAuthFilter> registration = new FilterRegistrationBean<>(agentAuthFilter);
		registration.setEnabled(false);
		return registration;
	}

	private final JwtAuthFilter jwtAuthFilter;
	private final ApiAccessLogFilter apiAccessLogFilter;
	private final ApiVersionStatusFilter apiVersionStatusFilter;
	private final GatewayApiKeyAuthFilter gatewayApiKeyAuthFilter;
	private final GatewayProperties gatewayProperties;
	private final SecurityErrorResponseHandler securityErrorResponseHandler;
	private final AppCorsProperties appCorsProperties;
	private final ExternalApiKeyFilter externalApiKeyFilter;

	/** 外部キー認証のServlet二重登録防止。
	 * @return 無効なServlet登録
	 */
	@Bean
	public FilterRegistrationBean<ExternalApiKeyFilter> externalApiKeyFilterRegistration() {
		FilterRegistrationBean<ExternalApiKeyFilter> registration = new FilterRegistrationBean<>(externalApiKeyFilter);
		registration.setEnabled(false);
		return registration;
	}

	/**
	 * JWT認証フィルターがServlet Filterとして二重登録されることを防ぐ。
	 *
	 * <p>JWT認証フィルターはSpring Securityのフィルターチェーン内で明示的に登録するため、
	 * TomcatのServlet Filterとしては登録しない。</p>
	 *
	 * @param filter JWT認証フィルター
	 * @return 無効化済みのServlet Filter登録Bean
	 */
	@Bean
	public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration(JwtAuthFilter filter) {
		FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>(filter);
		registration.setEnabled(false);
		return registration;
	}

	/**
	 * APIアクセスログフィルターのServlet Filterとしての二重登録を防ぐ。
	 *
	 * @return 無効化済みのServlet Filter登録Bean
	 */
	@Bean
	public FilterRegistrationBean<ApiAccessLogFilter> apiAccessLogFilterRegistration() {
		FilterRegistrationBean<ApiAccessLogFilter> registration =
				new FilterRegistrationBean<>(apiAccessLogFilter);
		registration.setEnabled(false);
		return registration;
	}

	/**
	 * Gateway APIキーフィルターがServlet Filterとして二重登録されることを防ぐ。
	 *
	 * <p>Gateway APIキーフィルターはSpring Securityのフィルターチェーン内で明示的に登録するため、
	 * TomcatのServlet Filterとしては登録しない。</p>
	 *
	 * @param filter Gateway APIキー認証フィルター
	 * @return 無効化済みのServlet Filter登録Bean
	 */
	@Bean
	public FilterRegistrationBean<GatewayApiKeyAuthFilter> gatewayApiKeyAuthFilterRegistration(
			GatewayApiKeyAuthFilter filter
	) {
		FilterRegistrationBean<GatewayApiKeyAuthFilter> registration = new FilterRegistrationBean<>(filter);
		registration.setEnabled(false);
		return registration;
	}

	/**
	 * APIバージョン判定フィルターのServlet Filterとしての二重登録を防ぐ。
	 *
	 * @return 無効化済みのServlet Filter登録Bean
	 */
	@Bean
	public FilterRegistrationBean<ApiVersionStatusFilter> apiVersionStatusFilterRegistration() {
		FilterRegistrationBean<ApiVersionStatusFilter> registration =
				new FilterRegistrationBean<>(apiVersionStatusFilter);
		registration.setEnabled(false);
		return registration;
	}

	/**
	 * Spring Securityが使用する認証マネージャーを公開する。
	 *
	 * @param configuration 認証設定
	 * @return 認証マネージャー
	 * @throws Exception 認証マネージャー生成に失敗した場合
	 */
	@Bean
	public AuthenticationManager authenticationManager(
			AuthenticationConfiguration configuration
	) throws Exception {
		return configuration.getAuthenticationManager();
	}

	/**
	 * パスワードのハッシュ化と照合に使用するエンコーダーを公開する。
	 *
	 * @return BCryptパスワードエンコーダー
	 */
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	/**
	 * API全体の認証、認可、JWTフィルタ、例外応答を設定する。
	 *
	 * @param http HTTPセキュリティ設定
	 * @return セキュリティフィルタチェーン
	 * @throws Exception セキュリティ設定に失敗した場合
	 */
	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.cors(Customizer.withDefaults())
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> {
					auth
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						// WebSocket自体は一回限りの接続チケットをHandshakeInterceptorで検証する。
						.requestMatchers("/ws/**").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/v1/agent/register",
								"/api/v1/agent/auth/challenge", "/api/v1/agent/auth/refresh").permitAll()
						.requestMatchers("/api/v1/agent/**").hasRole("AGENT")
						// 未実装バージョンへ認証条件を広げない。
						.requestMatchers("/api/v*/agent/**").denyAll()
						.requestMatchers("/api/v1/external/**").hasRole("EXTERNAL")
						.requestMatchers("/api/v*/external/**").denyAll()
						.requestMatchers("/api/external-api-keys", "/api/external-api-keys/**")
						.hasRole(ADMIN_ROLE.getStoredName())
						.requestMatchers("/api/auth/settings").hasRole(ADMIN_ROLE.getStoredName());
					if (gatewayProperties.getOpenapi().isPublicEnabled()) {
						auth.requestMatchers("/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/**",
							"/swagger-ui", "/swagger-ui/**", "/swagger-ui.html").permitAll();
					} else {
						auth.requestMatchers("/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/**",
							"/swagger-ui", "/swagger-ui/**", "/swagger-ui.html")
							.hasRole(ADMIN_ROLE.getStoredName());
					}
					auth
						.requestMatchers(
								"/api/auth/**",
								"/api/v*/iot/**",
								"/error"
						).permitAll()
						.anyRequest().hasAnyRole(ADMIN_ROLE.getStoredName(),
								OPERATOR_ROLE.getStoredName(), USER_ROLE.getStoredName());
				})
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.exceptionHandling(exception -> exception
						.authenticationEntryPoint((request, response, cause) ->
								securityErrorResponseHandler.write(request, response, 401,
										"Unauthorized", "UNAUTHORIZED", CommonMessageConstants.AUTHENTICATION_REQUIRED))
						.accessDeniedHandler((request, response, cause) ->
								securityErrorResponseHandler.write(request, response, 403,
										"Forbidden", "ACCESS_DENIED", CommonMessageConstants.ACCESS_DENIED)))
				.addFilterBefore(gatewayApiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
				.addFilterBefore(apiVersionStatusFilter, GatewayApiKeyAuthFilter.class)
				.addFilterBefore(apiAccessLogFilter, ApiVersionStatusFilter.class)
				.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
				.addFilterAfter(agentAuthFilter, JwtAuthFilter.class)
				.addFilterAfter(externalApiKeyFilter, AgentAuthFilter.class);

		return http.build();
	}

	/**
	 * フロントエンドからのアクセスを許可するCORS設定を公開する。
	 *
	 * @return CORS設定ソース
	 */
	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(appCorsProperties.getAllowedOrigins());
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("*"));
		configuration.setAllowCredentials(true);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}
}
