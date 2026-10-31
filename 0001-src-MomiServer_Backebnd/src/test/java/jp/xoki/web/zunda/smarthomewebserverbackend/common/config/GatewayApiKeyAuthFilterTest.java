package jp.xoki.web.zunda.smarthomewebserverbackend.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.SecurityErrorResponseHandler;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.time.ApplicationClockFactory;

class GatewayApiKeyAuthFilterTest {

	@Test
	void agentRequestsDoNotUseGatewayApiKeys() throws Exception {
		GatewayProperties properties = gatewayProperties(true, true, 1);
		properties.getApiKey().getIotKeys().add("iot-key");
		GatewayApiKeyAuthFilter filter = filter(properties);
		for (int i = 0; i < 2; i++) {
			MockHttpServletRequest request = request("/api/v1/agent/batch/settings");
			request.addHeader("X-API-Key", "iot-key");
			MockHttpServletResponse response = new MockHttpServletResponse();
			MockFilterChain chain = new MockFilterChain();
			filter.doFilter(request, response, chain);
			assertThat(chain.getRequest()).isSameAs(request);
			assertThat(request.getAttribute("gateway.clientType")).isEqualTo("agent");
			assertThat(response.getHeader("X-RateLimit-Limit")).isNull();
		}
	}

	@Test
	void doFilterAllowsExternalApiRequestWithValidApiKeyAndRateLimitHeaders() throws Exception {
		GatewayProperties properties = gatewayProperties(true, true, 2);
		properties.getApiKey().getIotKeys().add("iot-key");
		GatewayApiKeyAuthFilter filter = filter(properties);
		MockHttpServletRequest request = request("/api/v1/iot/telemetry");
		request.addHeader("X-API-Key", "iot-key");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		assertThat(chain.getRequest()).isSameAs(request);
		assertThat(request.getAttribute("gateway.apiVersion")).isEqualTo("v1");
		assertThat(request.getAttribute("gateway.clientType")).isEqualTo("iot");
		assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("2");
		assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("1");
	}

	@Test
	void doFilterRejectsExternalApiRequestWithoutApiKey() throws Exception {
		GatewayProperties properties = gatewayProperties(true, true, 60);
		properties.getApiKey().getIotKeys().add("iot-key");
		GatewayApiKeyAuthFilter filter = filter(properties);
		MockHttpServletRequest request = request("/api/v1/iot/telemetry");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		assertThat(chain.getRequest()).isNull();
		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getContentAsString()).contains("INVALID_GATEWAY_API_KEY");
	}

	@Test
	void contextPathDoesNotBypassIotApiKeyAuthentication() throws Exception {
		GatewayProperties properties = gatewayProperties(false, true, 60);
		properties.getApiKey().getIotKeys().add("iot-key");
		GatewayApiKeyAuthFilter filter = filter(properties);
		MockHttpServletRequest request = request("/api/v1/iot/telemetry");
		request.setContextPath("/server");
		request.setRequestURI("/server/api/v1/iot/telemetry");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		assertThat(chain.getRequest()).isNull();
		assertThat(response.getStatus()).isEqualTo(401);
	}

	@Test
	void doFilterReturnsTooManyRequestsWhenApiKeyRateLimitIsExceeded() throws Exception {
		GatewayProperties properties = gatewayProperties(true, true, 1);
		properties.getApiKey().getIotKeys().add("iot-key");
		GatewayApiKeyAuthFilter filter = filter(properties);

		MockHttpServletRequest firstRequest = request("/api/v1/iot/telemetry");
		firstRequest.addHeader("X-API-Key", "iot-key");
		filter.doFilter(firstRequest, new MockHttpServletResponse(), new MockFilterChain());

		MockHttpServletRequest secondRequest = request("/api/v1/iot/telemetry");
		secondRequest.addHeader("X-API-Key", "iot-key");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(secondRequest, response, chain);

		assertThat(chain.getRequest()).isNull();
		assertThat(response.getStatus()).isEqualTo(429);
		assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("1");
		assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
		assertThat(response.getHeader("Retry-After")).isEqualTo("60");
		assertThat(response.getContentAsString()).contains("GATEWAY_RATE_LIMIT_EXCEEDED");
	}

	@Test
	void doFilterAppliesRateLimitByRemoteAddressWhenApiKeyAuthIsDisabled() throws Exception {
		GatewayProperties properties = gatewayProperties(true, false, 1);
		GatewayApiKeyAuthFilter filter = filter(properties);
		MockHttpServletRequest firstRequest = request("/api/v1/iot/telemetry");
		firstRequest.setRemoteAddr("192.0.2.10");
		filter.doFilter(firstRequest, new MockHttpServletResponse(), new MockFilterChain());

		MockHttpServletRequest secondRequest = request("/api/v1/iot/telemetry");
		secondRequest.setRemoteAddr("192.0.2.10");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(secondRequest, response, chain);

		assertThat(chain.getRequest()).isNull();
		assertThat(response.getStatus()).isEqualTo(429);
		assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("1");
		assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
	}

	private GatewayApiKeyAuthFilter filter(GatewayProperties properties) {
		return new GatewayApiKeyAuthFilter(
				properties,
				new GatewayRateLimiter(properties, ApplicationClockFactory.systemClock()),
				new SecurityErrorResponseHandler(new ObjectMapper().findAndRegisterModules(), ApplicationClockFactory.systemClock())
		);
	}

	private GatewayProperties gatewayProperties(boolean rateLimitEnabled, boolean apiKeyEnabled, int perMinute) {
		GatewayProperties properties = new GatewayProperties();
		properties.getRateLimit().setEnabled(rateLimitEnabled);
		properties.getRateLimit().setPerMinute(perMinute);
		properties.getApiKey().setEnabled(apiKeyEnabled);
		return properties;
	}

	private MockHttpServletRequest request(String uri) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
		request.setServletPath(uri);
		return request;
	}
}
