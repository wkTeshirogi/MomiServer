package jp.xoki.web.zunda.smarthomewebserverbackend.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationLevel;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** 通知全体設定DTO。 */
@Getter
@Builder
public class NotificationGlobalSettingDetailDto {

	private Boolean enabled;
	@JsonProperty("browser_enabled")
	private Boolean browserEnabled;
	@JsonProperty("discord_enabled")
	private Boolean discordEnabled;
	@JsonProperty("discord_webhook_configured")
	private Boolean discordWebhookConfigured;
	@JsonProperty("discord_webhook_url")
	private String discordWebhookUrl;
	@JsonProperty("discord_minimum_level")
	private NotificationLevel discordMinimumLevel;
	@JsonProperty("security_event_discord_enabled")
	private Boolean securityEventDiscordEnabled;
	@JsonProperty("security_event_minimum_level")
	private NotificationLevel securityEventMinimumLevel;
	@JsonProperty("security_event_aggregation_window_seconds")
	private Integer securityEventAggregationWindowSeconds;
	@JsonProperty("retention_days")
	private Integer retentionDays;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
}
