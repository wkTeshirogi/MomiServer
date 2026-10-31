package jp.xoki.web.zunda.smarthomewebserverbackend.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationLevel;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationSourceType;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationTargetType;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** 通知ルート設定DTO。 */
@Getter
@Builder
public class NotificationRouteSettingDetailDto {

	private Long id;
	@JsonProperty("notification_type")
	private NotificationType notificationType;
	@JsonProperty("source_type")
	private NotificationSourceType sourceType;
	@JsonProperty("source_id")
	private String sourceId;
	@JsonProperty("target_type")
	private NotificationTargetType targetType;
	@JsonProperty("target_role")
	private String targetRole;
	@JsonProperty("target_user_id")
	private Long targetUserId;
	@JsonProperty("minimum_level")
	private NotificationLevel minimumLevel;
	@JsonProperty("browser_enabled")
	private Boolean browserEnabled;
	@JsonProperty("discord_enabled")
	private Boolean discordEnabled;
	private Boolean enabled;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
}
