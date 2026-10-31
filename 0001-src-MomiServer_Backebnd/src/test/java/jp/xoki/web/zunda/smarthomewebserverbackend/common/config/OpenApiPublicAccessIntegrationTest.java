package jp.xoki.web.zunda.smarthomewebserverbackend.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.regex.Matcher;
import org.springframework.mock.web.MockHttpServletResponse;

/** OpenAPIの公開設定が匿名要求に反映されることを検証する。 */
@SpringBootTest(properties = {
		"gateway.openapi.public-enabled=true",
		"mqtt.enabled=false",
		"notification.discord.enabled=false",
		"notification.discord.security-events.enabled=false",
		"spring.batch.job.enabled=false",
		"app.agent-auth.response-encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
@AutoConfigureMockMvc
class OpenApiPublicAccessIntegrationTest {

	@Autowired
	private MockMvc mvc;
	@Autowired
	private ObjectMapper mapper;

	@Test
	void publicOpenApiAllowsAnonymousRequests() throws Exception {
		MockHttpServletResponse response = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
				.andReturn().getResponse();
		JsonNode api = mapper.readTree(response.getContentAsString());
		assertThat(api.at("/paths/~1api~1v1~1agent~1me/get/security/0/agentBearer").isArray())
				.isTrue();
		assertThat(api.at("/paths/~1api~1v1~1agent~1register/post/security").isArray())
				.isTrue();
		assertThat(api.at("/paths/~1api~1v1~1agent~1register/post/security").size())
				.isZero();
		mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
		mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
		mvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk());
		mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk());
	}

	@Test
	void publicSwaggerServesPatchedBundlesAndOauthRedirectAssets() throws Exception {
		for (String bundle : List.of("swagger-ui-bundle.js", "swagger-ui-es-bundle.js")) {
			String body = mvc.perform(get("/swagger-ui/" + bundle)).andExpect(status().isOk())
					.andReturn().getResponse().getContentAsString();
			Matcher version = Pattern.compile("DOMPurify\\.version=\"(\\d+\\.\\d+\\.\\d+)\"").matcher(body);
			assertThat(version.find()).as("Bundled DOMPurify version in %s", bundle).isTrue();
			assertThat(Runtime.Version.parse(version.group(1)))
					.isGreaterThanOrEqualTo(Runtime.Version.parse("3.4.16"));
		}
		mvc.perform(get("/swagger-ui/swagger-initializer.js")).andExpect(status().isOk());
		mvc.perform(get("/swagger-ui/oauth2-redirect.html")).andExpect(status().isOk());
		mvc.perform(get("/swagger-ui/oauth2-redirect.js")).andExpect(status().isOk());
	}
}
