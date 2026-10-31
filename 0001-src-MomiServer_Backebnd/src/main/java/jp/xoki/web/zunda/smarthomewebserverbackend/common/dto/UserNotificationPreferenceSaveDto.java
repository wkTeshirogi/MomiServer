package jp.xoki.web.zunda.smarthomewebserverbackend.common.dto;

import jakarta.validation.constraints.NotNull;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationLevel;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationType;

/** ユーザー別通知受信設定の保存内容。 */
public record UserNotificationPreferenceSaveDto(
		@NotNull NotificationType notification_type,
		@NotNull NotificationLevel minimum_level,
		@NotNull Boolean browser_enabled,
		@NotNull Boolean discord_enabled,
		@NotNull Boolean enabled
) {
}
