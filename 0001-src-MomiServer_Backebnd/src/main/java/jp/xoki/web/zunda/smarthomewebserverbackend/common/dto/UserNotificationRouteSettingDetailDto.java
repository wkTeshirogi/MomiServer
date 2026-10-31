package jp.xoki.web.zunda.smarthomewebserverbackend.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationLevel;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationSourceType;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** 個別ユーザー宛て通知ルート設定DTO。 */
@Getter
@Builder
public class UserNotificationRouteSettingDetailDto {

	/** 通知ルート設定ID。 */
	private Long id;

	/** 通知種別。 */
	@JsonProperty("notification_type")
	private NotificationType notificationType;

	/** 通知発生元種別。 */
	@JsonProperty("source_type")
	private NotificationSourceType sourceType;

	/** 発生元データID。 */
	@JsonProperty("source_id")
	private String sourceId;

	/** 通知対象ユーザーID。 */
	@JsonProperty("target_user_id")
	private Long targetUserId;

	/** 最低通知レベル。 */
	@JsonProperty("minimum_level")
	private NotificationLevel minimumLevel;

	/** ブラウザ通知を送信するか。 */
	@JsonProperty("browser_enabled")
	private Boolean browserEnabled;

	/** Discord通知を送信するか。 */
	@JsonProperty("discord_enabled")
	private Boolean discordEnabled;

	/** 通知ルートを有効にするか。 */
	private Boolean enabled;

	/** 作成日時。 */
	private LocalDateTime createdAt;

	/** 更新日時。 */
	private LocalDateTime updatedAt;
}
