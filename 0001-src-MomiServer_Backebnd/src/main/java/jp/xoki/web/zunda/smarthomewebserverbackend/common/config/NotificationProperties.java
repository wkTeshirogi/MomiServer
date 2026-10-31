package jp.xoki.web.zunda.smarthomewebserverbackend.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 通知機能全体の設定値を保持する設定クラス。 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "notification")
public class NotificationProperties {

	/** Discord Webhook通知に関する設定。 */
	private Discord discord = new Discord();

	/** Discord Webhook通知に関する設定値。 */
	@Getter
	@Setter
	public static class Discord {

		/** Discord通知全体を有効にするかどうか。 */
		private boolean enabled = false;

		/** Discord Webhook URL。Bot Tokenは使用しない。 */
		private String webhookUrl = "";

		/** 不正アクセス監査イベント向けDiscord通知設定。 */
		private SecurityEvents securityEvents = new SecurityEvents();
	}

	/** 不正アクセス監査イベント向けDiscord通知設定値。 */
	@Getter
	@Setter
	public static class SecurityEvents {

		/** 不正アクセス監査イベントのDiscord通知を有効にするかどうか。 */
		private boolean enabled = false;

		/** Discord通知対象とする最低重要度。 */
		private String minimumSeverity = "warning";

		/** 同種イベントを集約通知する時間窓の秒数。 */
		private int aggregationWindowSeconds = 300;
	}
}
