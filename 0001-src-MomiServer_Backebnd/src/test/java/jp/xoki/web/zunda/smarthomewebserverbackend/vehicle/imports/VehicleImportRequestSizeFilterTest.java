package jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.imports;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class VehicleImportRequestSizeFilterTest {

	@Test
	void rejectsOversizedAndLengthlessUploadBeforeMultipartParsing() throws Exception {
		VehicleImportProperties properties = new VehicleImportProperties();
		properties.setMaxBytes(100);
		VehicleImportRequestSizeFilter filter = new VehicleImportRequestSizeFilter(properties);

		MockHttpServletRequest oversized = request();
		oversized.setContent(new byte[65_638]);
		MockHttpServletResponse oversizedResponse = new MockHttpServletResponse();
		filter.doFilter(oversized, oversizedResponse, new MockFilterChain());
		assertThat(oversizedResponse.getStatus()).isEqualTo(413);

		MockHttpServletRequest lengthless = request();
		MockHttpServletResponse lengthlessResponse = new MockHttpServletResponse();
		filter.doFilter(lengthless, lengthlessResponse, new MockFilterChain());
		assertThat(lengthlessResponse.getStatus()).isEqualTo(411);
	}

	private MockHttpServletRequest request() {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/vehicle-imports");
		request.setContentType("multipart/form-data; boundary=test");
		return request;
	}
}
