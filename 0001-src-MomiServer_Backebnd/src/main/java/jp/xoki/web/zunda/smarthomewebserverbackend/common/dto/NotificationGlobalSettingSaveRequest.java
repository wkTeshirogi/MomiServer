package jp.xoki.web.zunda.smarthomewebserverbackend.common.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationLevel;

/** 通知全体設定の保存リクエスト。 */
public record NotificationGlobalSettingSaveRequest(
		@NotNull Boolean enabled,
		@NotNull Boolean browser_enabled,
		@NotNull Boolean discord_enabled,
		@Size(max = 2000) String discord_webhook_url,
		@NotNull NotificationLevel discord_minimum_level,
		@NotNull Boolean security_event_discord_enabled,
		@NotNull NotificationLevel security_event_minimum_level,
		@NotNull @Min(1) Integer security_event_aggregation_window_seconds,
		@NotNull @Min(1) Integer retention_days
) {
}
