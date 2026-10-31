package jp.xoki.web.zunda.smarthomewebserverbackend.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** APIアクセスログの記録・保持設定。 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.api-access-log")
public class ApiAccessLogProperties {

	/** APIアクセスログをDBへ記録するかどうか。 */
	private boolean enabled = true;

	/** APIアクセスログを保持する日数。 */
	private int retentionDays = 30;
}
