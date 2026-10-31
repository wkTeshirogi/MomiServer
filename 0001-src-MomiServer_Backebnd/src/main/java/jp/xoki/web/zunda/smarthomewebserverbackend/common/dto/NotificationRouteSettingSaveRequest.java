package jp.xoki.web.zunda.smarthomewebserverbackend.common.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationLevel;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationSourceType;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationTargetType;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationType;

/** 通知ルート設定の保存リクエスト。 */
public record NotificationRouteSettingSaveRequest(
		Long id,
		@NotNull NotificationType notification_type,
		@NotNull NotificationSourceType source_type,
		@Size(max = 100) String source_id,
		@NotNull NotificationTargetType target_type,
		@Size(max = 50) String target_role,
		Long target_user_id,
		@NotNull NotificationLevel minimum_level,
		@NotNull Boolean browser_enabled,
		@NotNull Boolean discord_enabled,
		@NotNull Boolean enabled
) {
}
