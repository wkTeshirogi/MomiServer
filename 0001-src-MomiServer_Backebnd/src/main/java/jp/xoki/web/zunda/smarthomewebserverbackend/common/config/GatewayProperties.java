package jp.xoki.web.zunda.smarthomewebserverbackend.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Gateway APIの認証、レート制限、OpenAPI公開設定を保持する設定クラス。 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

	/** Gateway制御全体を有効にするかどうか。 */
	private boolean enabled = true;

	/** Gateway APIで既定として扱うAPIバージョン。 */
	private String defaultApiVersion = "v1";

	/** IoT APIで利用するAPIキー設定。 */
	private ApiKey apiKey = new ApiKey();

	/** Gateway対象APIへ適用するレート制限設定。 */
	private RateLimit rateLimit = new RateLimit();

	/** Gateway経由のOpenAPI公開設定。 */
	private OpenApi openapi = new OpenApi();

	/** Gateway APIキー認証に関する設定。 */
	@Getter
	@Setter
	public static class ApiKey {

		/** APIキー認証を有効にするかどうか。 */
		private boolean enabled = true;

		/** APIキーを受け取るHTTPヘッダー名。 */
		private String headerName = "X-API-Key";

		/** IoT APIへのアクセスを許可するAPIキー一覧。 */
		private List<String> iotKeys = new ArrayList<>();
	}

	/** Gateway対象APIのレート制限に関する設定。 */
	@Getter
	@Setter
	public static class RateLimit {

		/** レート制限を有効にするかどうか。 */
		private boolean enabled = false;

		/** 1分間に許可するリクエスト数。 */
		private int perMinute = 60;
	}

	/** Gateway経由で公開するOpenAPIに関する設定。 */
	@Getter
	@Setter
	public static class OpenApi {

		/** OpenAPIエンドポイントを公開するかどうか。 */
		private boolean publicEnabled = true;
	}
}
