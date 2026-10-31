package jp.xoki.web.zunda.smarthomewebserverbackend.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.SpringDocConfigConstants.AGENT_BEARER;
import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.SpringDocConfigConstants.EXTERNAL_API_KEY;
import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.SpringDocConfigConstants.IOT_API_KEY;
import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.SpringDocConfigConstants.USER_BEARER;

/** OpenAPIとSwagger UIで利用するAPIドキュメント設定を提供するクラス。 */
@Configuration
@RequiredArgsConstructor
public class SpringDocConfig {


	private final GatewayProperties gatewayProperties;

	/**
	 * API基本情報と利用元別の認証スキームを設定する。
	 *
	 * @return OpenAPI設定
	 */
	@Bean
	public OpenAPI openAPI() {
		return new OpenAPI()
				.info(new Info()
						.title("SmartHomeWebServerBackend API")
						.description("SmartHome バックエンドAPI")
						.version("0.0.1-SNAPSHOT"))
				.components(new Components()
						.addSecuritySchemes(USER_BEARER, new SecurityScheme()
								.type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
								.description("WebユーザーのJWTアクセストークン"))
						.addSecuritySchemes(AGENT_BEARER, new SecurityScheme()
								.type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("opaque")
								.description("登録済みAgentの専用アクセストークン"))
						.addSecuritySchemes(EXTERNAL_API_KEY, new SecurityScheme()
								.type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER)
								.name("X-API-Key").description("外部在庫API専用キー"))
						.addSecuritySchemes(IOT_API_KEY, new SecurityScheme()
								.type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER)
								.name(gatewayProperties.getApiKey().getHeaderName())
								.description("IoT向けGateway APIキー")));
	}

	/**
	 * 各APIの認証要件をSecurityFilterChainの経路区分に合わせる。
	 *
	 * @return OpenAPI操作ごとの認証設定
	 */
	@Bean
	public OpenApiCustomizer apiSecurityCustomizer() {
		return openApi -> {
			if (openApi.getPaths() == null) {
				return;
			}
			openApi.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
				String scheme = securityScheme(path, method.name());
				operation.setSecurity(scheme == null
						? List.of()
						: List.of(new SecurityRequirement().addList(scheme)));
			}));
		};
	}

	private String securityScheme(String path, String method) {
		if (path.matches("/api/v\\d+/external(?:/.*)?")) {
			return EXTERNAL_API_KEY;
		}
		if ("POST".equals(method) && ("/api/v1/agent/register".equals(path)
				|| "/api/v1/agent/auth/challenge".equals(path)
				|| "/api/v1/agent/auth/refresh".equals(path))) {
			return null;
		}
		if (path.matches("/api/v\\d+/agent(?:/.*)?")) {
			return AGENT_BEARER;
		}
		if (path.matches("/api/v\\d+/iot(?:/.*)?")) {
			return gatewayProperties.isEnabled() && gatewayProperties.getApiKey().isEnabled()
					? IOT_API_KEY : null;
		}
		if ("/api/auth/login".equals(path) || "/api/auth/refresh".equals(path)
				|| "/api/auth/logout".equals(path)) {
			return null;
		}
		return USER_BEARER;
	}
}
