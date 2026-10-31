package jp.xoki.web.zunda.smarthomewebserverbackend.common.config;

import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.ApiAccessLogWriteRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.ApiAccessLogAttributes;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.service.ApiAccessLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.time.ApplicationClockFactory;

@ExtendWith(MockitoExtension.class)
class ApiAccessLogFilterTest {

	@Mock
	private ApiAccessLogService service;

	private ApiAccessLogFilter filter;

	@BeforeEach
	void setUp() {
		filter = new ApiAccessLogFilter(service, new ApiAccessLogProperties(), ApplicationClockFactory.systemClock());
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void recordsResolvedApiMetadataWithoutHeadersOrBody() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/stock/save");
		request.setRemoteAddr("127.0.0.1");
		request.addHeader("User-Agent", "test-agent");
		request.addHeader("Authorization", "Bearer secret-token");
		request.setContent("{\"password\":\"secret\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		MockHttpServletResponse response = new MockHttpServletResponse();
		SecurityContextHolder.getContext().setAuthentication(
				UsernamePasswordAuthenticationToken.authenticated(
						"operator@example.com", null, java.util.List.of()));

		filter.doFilter(request, response, (req, res) -> {
			req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/stock/save");
			req.setAttribute(ApiAccessLogAttributes.ERROR_CODE, "TEST_ERROR");
			((jakarta.servlet.http.HttpServletResponse) res).setStatus(422);
		});

		ArgumentCaptor<ApiAccessLogWriteRequest> captor =
				ArgumentCaptor.forClass(ApiAccessLogWriteRequest.class);
		verify(service).write(captor.capture());
		ApiAccessLogWriteRequest actual = captor.getValue();
		assertThat(actual.http_method()).isEqualTo("POST");
		assertThat(actual.request_path()).isEqualTo("/api/stock/save");
		assertThat(actual.endpoint_pattern()).isEqualTo("/api/stock/save");
		assertThat(actual.status_code()).isEqualTo(422);
		assertThat(actual.source_type()).isEqualTo("web");
		assertThat(actual.principal()).isEqualTo("operator@example.com");
		assertThat(actual.error_code()).isEqualTo("TEST_ERROR");
		assertThat(actual.toString()).doesNotContain("secret-token", "password", "secret");
		assertThat(response.getHeader("X-Request-ID")).isEqualTo(actual.request_id().toString());
	}

	@Test
	void externalRequestsRecordKeyIdentityWithoutSecret() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/external/inventory/stocks");
		request.addHeader("X-API-Key", "secret-external-key");
		SecurityContextHolder.getContext().setAuthentication(
				UsernamePasswordAuthenticationToken.authenticated("external:key-id", null, java.util.List.of()));
		filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
		ArgumentCaptor<ApiAccessLogWriteRequest> captor = ArgumentCaptor.forClass(ApiAccessLogWriteRequest.class);
		verify(service).write(captor.capture());
		assertThat(captor.getValue().source_type()).isEqualTo("gateway");
		assertThat(captor.getValue().principal()).isEqualTo("external:key-id");
		assertThat(captor.getValue().toString()).doesNotContain("secret-external-key");
	}

	@Test
	void persistenceFailureDoesNotChangeApiResponse() throws Exception {
		doThrow(new IllegalStateException("database unavailable")).when(service).write(any());
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/status/services");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, (req, res) ->
				((jakarta.servlet.http.HttpServletResponse) res).setStatus(204));

		assertThat(response.getStatus()).isEqualTo(204);
	}

	@ParameterizedTest
	@CsvSource({
			"/api/v1/agent, agent", "/api/v12/agent/status, agent", "/api/v1/iot/status, iot",
			"/api/v1/external, gateway", "/api/v2/external/inventory, gateway",
			"/context/api/v1/agent/status, agent", "/api/v1/agents, anonymous",
			"/api/v1/iot-extra, anonymous", "/api/vx/external, anonymous",
			"/api/v001/agent, agent", "/api/v\u0661/agent, anonymous", "/api/v\uFF11/agent, anonymous",
			"/api/v1/agent/api/v1/external, gateway", "/api/v1/iot/api/v1/agent, agent"
	})
	void sourceClassificationRetainsVersionContextAndSegmentBoundaries(String path, String expected) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
		if (path.startsWith("/context/")) request.setContextPath("/context");
		filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
		ArgumentCaptor<ApiAccessLogWriteRequest> captor = ArgumentCaptor.forClass(ApiAccessLogWriteRequest.class);
		verify(service).write(captor.capture());
		assertThat(captor.getValue().source_type()).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource({"/api/v1/iot/status, agent, agent", "/api/v1/agent/status, iot, iot",
			"/api/v1/external/inventory, agent, gateway"})
	void sourceClassificationPreservesGatewayAttributePrecedence(String path, String gatewayType, String expected) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
		request.setAttribute("gateway.clientType", gatewayType);
		filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
		ArgumentCaptor<ApiAccessLogWriteRequest> captor = ArgumentCaptor.forClass(ApiAccessLogWriteRequest.class);
		verify(service).write(captor.capture());
		assertThat(captor.getValue().source_type()).isEqualTo(expected);
	}

	@Test
	void skipsOptionsAndNonApiRequests() throws Exception {
		filter.doFilter(new MockHttpServletRequest("OPTIONS", "/api/status/services"),
				new MockHttpServletResponse(), new MockFilterChain());
		filter.doFilter(new MockHttpServletRequest("GET", "/swagger-ui/index.html"),
				new MockHttpServletResponse(), new MockFilterChain());

		verify(service, never()).write(any());
	}
}
