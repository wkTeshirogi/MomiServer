package jp.xoki.web.zunda.smarthomewebserverbackend.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationLevel;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** ユーザー別通知受信設定DTO。 */
@Getter
@Builder
public class UserNotificationPreferenceDetailDto {

	/** 通知対象ユーザーID。 */
	@JsonProperty("user_id")
	private Long userId;

	/** 通知種別。 */
	@JsonProperty("notification_type")
	private NotificationType notificationType;

	/** 最低通知レベル。 */
	@JsonProperty("minimum_level")
	private NotificationLevel minimumLevel;

	/** ブラウザ通知を受け取るか。 */
	@JsonProperty("browser_enabled")
	private Boolean browserEnabled;

	/** Discord通知を受け取るか。 */
	@JsonProperty("discord_enabled")
	private Boolean discordEnabled;

	/** 通知種別全体を有効にするか。 */
	private Boolean enabled;

	/** 作成日時。 */
	private LocalDateTime createdAt;

	/** 更新日時。 */
	private LocalDateTime updatedAt;
}
