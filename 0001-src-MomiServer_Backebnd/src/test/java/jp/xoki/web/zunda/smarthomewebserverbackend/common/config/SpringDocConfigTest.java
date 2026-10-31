package jp.xoki.web.zunda.smarthomewebserverbackend.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpringDocConfigTest {

	@Test
	void operationsDescribeTheirActualAuthenticationBoundary() {
		GatewayProperties properties = new GatewayProperties();
		properties.getApiKey().setHeaderName("X-Device-Key");
		SpringDocConfig config = new SpringDocConfig(properties);
		OpenAPI api = config.openAPI().paths(new Paths()
				.addPathItem("/api/auth/login", new PathItem().post(new Operation()))
				.addPathItem("/api/auth/settings", new PathItem().post(new Operation()))
				.addPathItem("/api/v1/agent/register", new PathItem().post(new Operation()))
				.addPathItem("/api/v1/agent/me", new PathItem().get(new Operation()))
				.addPathItem("/api/v1/iot/telemetry", new PathItem().post(new Operation()))
				.addPathItem("/api/v1/external/inventory/stocks", new PathItem().get(new Operation()))
				.addPathItem("/api/stock/list", new PathItem().post(new Operation())));

		config.apiSecurityCustomizer().customise(api);

		assertThat(api.getSecurity()).isNull();
		assertThat(api.getComponents().getSecuritySchemes().get("externalApiKey").getName())
				.isEqualTo("X-API-Key");
		assertThat(api.getPaths().get("/api/v1/external/inventory/stocks").getGet().getSecurity().getFirst())
				.containsKey("externalApiKey");
		assertThat(api.getComponents().getSecuritySchemes().get("iotApiKey").getName())
				.isEqualTo("X-Device-Key");
		assertThat(api.getPaths().get("/api/auth/login").getPost().getSecurity()).isEmpty();
		assertThat(api.getPaths().get("/api/v1/agent/register").getPost().getSecurity()).isEmpty();
		assertThat(api.getPaths().get("/api/auth/settings").getPost().getSecurity().getFirst())
				.containsKey("userBearer");
		assertThat(api.getPaths().get("/api/v1/agent/me").getGet().getSecurity().getFirst())
				.containsKey("agentBearer");
		assertThat(api.getPaths().get("/api/v1/iot/telemetry").getPost().getSecurity().getFirst())
				.containsKey("iotApiKey");
		assertThat(api.getPaths().get("/api/stock/list").getPost().getSecurity().getFirst())
				.containsKey("userBearer");
	}

	@Test
	void iotOperationsArePublicWhenGatewayKeyAuthenticationIsDisabled() {
		GatewayProperties properties = new GatewayProperties();
		properties.getApiKey().setEnabled(false);
		SpringDocConfig config = new SpringDocConfig(properties);
		OpenAPI api = config.openAPI().paths(new Paths()
				.addPathItem("/api/v1/iot/telemetry", new PathItem().post(new Operation())));

		config.apiSecurityCustomizer().customise(api);

		assertThat(api.getPaths().get("/api/v1/iot/telemetry").getPost().getSecurity()).isEmpty();
	}
}
