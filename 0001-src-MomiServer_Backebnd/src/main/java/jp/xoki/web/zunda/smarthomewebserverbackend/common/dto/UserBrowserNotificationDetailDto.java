package jp.xoki.web.zunda.smarthomewebserverbackend.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationDeliveryStatus;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationLevel;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationReadStatus;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationSourceType;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/** ユーザー別ブラウザ通知状態を含む通知詳細DTO。 */
@Getter
@Builder
public class UserBrowserNotificationDetailDto {

	/** ユーザー別通知状態のDB内部ID。 */
	private Long id;

	/** 外部参照用通知ID。 */
	@JsonProperty("notification_id")
	private UUID notificationId;

	/** 通知対象ユーザーID。 */
	@JsonProperty("user_id")
	private Long userId;

	/** 配信状態。 */
	@JsonProperty("delivery_status")
	private NotificationDeliveryStatus deliveryStatus;

	/** 既読状態。 */
	@JsonProperty("read_status")
	private NotificationReadStatus readStatus;

	/** 配信日時。 */
	@JsonProperty("delivered_at")
	private LocalDateTime deliveredAt;

	/** 既読日時。 */
	@JsonProperty("read_at")
	private LocalDateTime readAt;

	/** 非表示日時。 */
	@JsonProperty("dismissed_at")
	private LocalDateTime dismissedAt;

	/** 通知種別。 */
	@JsonProperty("notification_type")
	private NotificationType notificationType;

	/** 通知レベル。 */
	private NotificationLevel level;

	/** 通知タイトル。 */
	private String title;

	/** 通知本文。 */
	private String message;

	/** 通知発生元種別。 */
	@JsonProperty("source_type")
	private NotificationSourceType sourceType;

	/** 発生元データID。 */
	@JsonProperty("source_id")
	private String sourceId;

	/** 通知クリック時の遷移先。 */
	@JsonProperty("link_path")
	private String linkPath;

	/** 補足情報JSON文字列。 */
	@JsonProperty("metadata_json")
	private String metadataJson;

	/** 表示期限。 */
	@JsonProperty("expires_at")
	private LocalDateTime expiresAt;

	/** 作成日時。 */
	private LocalDateTime createdAt;

	/** 更新日時。 */
	private LocalDateTime updatedAt;
}
