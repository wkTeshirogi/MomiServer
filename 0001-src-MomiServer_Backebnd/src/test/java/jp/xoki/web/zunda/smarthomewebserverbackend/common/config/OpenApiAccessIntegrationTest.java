package jp.xoki.web.zunda.smarthomewebserverbackend.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** OpenAPI公開設定が実際の経路アクセスに反映されることを検証する。 */
@SpringBootTest(properties = {
		"gateway.openapi.public-enabled=false",
		"mqtt.enabled=false",
		"notification.discord.enabled=false",
		"notification.discord.security-events.enabled=false",
		"spring.batch.job.enabled=false",
		"app.agent-auth.response-encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
@AutoConfigureMockMvc
class OpenApiAccessIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Test
	void privateOpenApiRequiresAdminRole() throws Exception {
		for (String path : new String[]{"/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/swagger-config",
				"/swagger-ui/index.html", "/swagger-ui.html"}) {
			mvc.perform(get(path)).andExpect(status().isUnauthorized());
		}
		mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
		mvc.perform(get("/v3/api-docs").with(user("reader").roles("USER")))
				.andExpect(status().isForbidden());
		mvc.perform(get("/v3/api-docs.yaml").with(user("reader").roles("USER")))
				.andExpect(status().isForbidden());
		mvc.perform(get("/swagger-ui").with(user("reader").roles("USER")))
				.andExpect(status().isForbidden());
		mvc.perform(get("/v3/api-docs").with(user("admin").roles("ADMIN")))
				.andExpect(status().isOk());
		mvc.perform(get("/swagger-ui.html")).andExpect(status().isUnauthorized());
	}
}
