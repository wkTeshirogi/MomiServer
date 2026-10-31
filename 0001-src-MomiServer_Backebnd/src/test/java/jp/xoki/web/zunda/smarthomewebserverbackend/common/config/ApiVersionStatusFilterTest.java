package jp.xoki.web.zunda.smarthomewebserverbackend.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.SecurityErrorResponseHandler;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.repository.ApiVersionStatusRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.repository.ApiVersionStatusRepository.ApiVersionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.time.ApplicationClockFactory;

class ApiVersionStatusFilterTest {

	private final ApiVersionStatusRepository repository = mock(ApiVersionStatusRepository.class);
	private final ApiVersionStatusFilter filter = new ApiVersionStatusFilter(repository,
			new SecurityErrorResponseHandler(new ObjectMapper().findAndRegisterModules(), ApplicationClockFactory.systemClock()));

	@Test
	void activeAgentVersionContinuesToDedicatedAuthentication() throws Exception {
		when(repository.find("agent", "v1")).thenReturn(Optional.of(new ApiVersionStatus("active", 1L)));
		MockHttpServletRequest request = request("/api/v1/agent/me");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		assertThat(chain.getRequest()).isSameAs(request);
		assertThat(response.getHeader("Deprecation")).isNull();
	}

	@Test
	void deprecatedIotVersionAddsStandardsCompliantHeader() throws Exception {
		when(repository.find("iot", "v1"))
				.thenReturn(Optional.of(new ApiVersionStatus("deprecated", 1767193200L)));
		MockHttpServletRequest request = request("/api/v1/iot/telemetry");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		assertThat(chain.getRequest()).isSameAs(request);
		assertThat(response.getHeader("Deprecation")).isEqualTo("@1767193200");
	}

	@Test
	void retiredVersionIsUnavailable() throws Exception {
		when(repository.find("agent", "v1"))
				.thenReturn(Optional.of(new ApiVersionStatus("retired", 1L)));
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request("/api/v1/agent/me"), response, chain);

		assertThat(chain.getRequest()).isNull();
		assertThat(response.getStatus()).isEqualTo(410);
		assertThat(response.getContentAsString()).contains("API_VERSION_RETIRED");
	}

	@Test
	void unimplementedVersionIsRejectedEvenIfDatabaseWouldMarkItActive() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request("/api/v2/agent/me"), response, chain);

		assertThat(chain.getRequest()).isNull();
		assertThat(response.getStatus()).isEqualTo(404);
		verifyNoInteractions(repository);
	}

	@Test
	void missingVersionIsRejected() throws Exception {
		when(repository.find("iot", "v1")).thenReturn(Optional.empty());
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request("/api/v1/iot/telemetry"), response, chain);

		assertThat(chain.getRequest()).isNull();
		assertThat(response.getStatus()).isEqualTo(404);
	}

	@Test
	void databaseFailureDoesNotBypassVersionCheck() throws Exception {
		when(repository.find("agent", "v1"))
				.thenThrow(new DataAccessResourceFailureException("database unavailable"));
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request("/api/v1/agent/me"), response, chain);

		assertThat(chain.getRequest()).isNull();
		assertThat(response.getStatus()).isEqualTo(503);
	}

	@Test
	void contextPathDoesNotBypassVersionCheck() throws Exception {
		when(repository.find("agent", "v1"))
				.thenReturn(Optional.of(new ApiVersionStatus("retired", 1L)));
		MockHttpServletRequest request = request("/api/v1/agent/me");
		request.setContextPath("/server");
		request.setRequestURI("/server/api/v1/agent/me");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		assertThat(chain.getRequest()).isNull();
		assertThat(response.getStatus()).isEqualTo(410);
	}

	private MockHttpServletRequest request(String path) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
		request.setServletPath(path);
		return request;
	}
}
