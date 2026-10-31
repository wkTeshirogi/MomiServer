package jp.xoki.web.zunda.smarthomewebserverbackend.common.config;

import jp.xoki.web.zunda.smarthomewebserverbackend.agent.config.AgentAuthFilter;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.SecurityErrorResponseHandler;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SecurityConfigTest {

	@Test
	void corsConfigurationSourceUsesConfiguredAllowedOrigins() {
		AppCorsProperties corsProperties = new AppCorsProperties();
		corsProperties.setAllowedOrigins(List.of("http://localhost:5173", "https://example.com"));
		SecurityConfig securityConfig = new SecurityConfig(
                mock(AgentAuthFilter.class),
				mock(JwtAuthFilter.class),
				mock(ApiAccessLogFilter.class),
				mock(ApiVersionStatusFilter.class),
				mock(GatewayApiKeyAuthFilter.class),
				new GatewayProperties(),
				mock(SecurityErrorResponseHandler.class),
				corsProperties,
				mock(jp.xoki.web.zunda.smarthomewebserverbackend.external.config.ExternalApiKeyFilter.class)
		);

		CorsConfigurationSource source = securityConfig.corsConfigurationSource();
		CorsConfiguration configuration = source.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/auth/login"));

		assertThat(configuration).isNotNull();
		assertThat(configuration.getAllowedOrigins())
				.containsExactly("http://localhost:5173", "https://example.com");
		assertThat(configuration.getAllowedMethods())
				.containsExactly("GET", "POST", "PUT", "DELETE", "OPTIONS");
		assertThat(configuration.getAllowedHeaders()).containsExactly("*");
		assertThat(configuration.getAllowCredentials()).isTrue();
	}
}
