package jp.xoki.web.zunda.smarthomewebserverbackend.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationLevel;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationSourceType;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** ブラウザ通知本体の作成内容を保持するDTO。 */
@Getter
@Builder
public class BrowserNotificationCreateDto {

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
}
