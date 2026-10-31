package jp.xoki.web.zunda.smarthomewebserverbackend.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** フロントエンドからのアクセスを許可するCORS設定を保持する設定クラス。 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.cors")
public class AppCorsProperties {

	/** 許可するフロントエンドのOrigin一覧。 */
	private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173"));
}
