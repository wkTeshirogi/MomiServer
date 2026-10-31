package jp.xoki.web.zunda.smarthomewebserverbackend.common.repository;

import jp.xoki.web.zunda.jooq.tables.TNotificationGlobalSettings;

import java.time.LocalDateTime;
import java.util.Optional;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.NotificationGlobalSettingDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.NotificationGlobalSettingSaveRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationLevel;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.NotificationGlobalSettingRepositoryConstants.SINGLETON_ID;
import java.time.Clock;

/** 通知全体設定テーブルへアクセスするRepository。 */
@Repository
@RequiredArgsConstructor
public class NotificationGlobalSettingRepository {

	/** 通知全体設定テーブル。 */
	private static final TNotificationGlobalSettings T_NOTIFICATION_GLOBAL_SETTINGS = TNotificationGlobalSettings.T_NOTIFICATION_GLOBAL_SETTINGS;


	private static final Field<Short> ID = T_NOTIFICATION_GLOBAL_SETTINGS.ID;
	private static final Field<Boolean> ENABLED = T_NOTIFICATION_GLOBAL_SETTINGS.ENABLED;
	private static final Field<Boolean> BROWSER_ENABLED = T_NOTIFICATION_GLOBAL_SETTINGS.BROWSER_ENABLED;
	private static final Field<Boolean> DISCORD_ENABLED = T_NOTIFICATION_GLOBAL_SETTINGS.DISCORD_ENABLED;
	private static final Field<String> DISCORD_WEBHOOK_URL = T_NOTIFICATION_GLOBAL_SETTINGS.DISCORD_WEBHOOK_URL;
	private static final Field<String> DISCORD_MINIMUM_LEVEL = T_NOTIFICATION_GLOBAL_SETTINGS.DISCORD_MINIMUM_LEVEL;
	private static final Field<Boolean> SECURITY_EVENT_DISCORD_ENABLED =
			T_NOTIFICATION_GLOBAL_SETTINGS.SECURITY_EVENT_DISCORD_ENABLED;
	private static final Field<String> SECURITY_EVENT_MINIMUM_LEVEL =
			T_NOTIFICATION_GLOBAL_SETTINGS.SECURITY_EVENT_MINIMUM_LEVEL;
	private static final Field<Integer> SECURITY_EVENT_AGGREGATION_WINDOW_SECONDS =
			T_NOTIFICATION_GLOBAL_SETTINGS.SECURITY_EVENT_AGGREGATION_WINDOW_SECONDS;
	private static final Field<Integer> RETENTION_DAYS = T_NOTIFICATION_GLOBAL_SETTINGS.RETENTION_DAYS;
	private static final Field<LocalDateTime> CREATED_DT = T_NOTIFICATION_GLOBAL_SETTINGS.CREATED_DT;
	private static final Field<LocalDateTime> UPDATED_DT = T_NOTIFICATION_GLOBAL_SETTINGS.UPDATED_DT;

	/** jOOQ DSL。 */
	private final DSLContext dsl;

	private final Clock clock;

	/**
	 * 通知全体設定を取得する。
	 *
	 * @return 通知全体設定
	 */
	public Optional<NotificationGlobalSettingDetailDto> find() {
		return dsl.select(
						ENABLED,
						BROWSER_ENABLED,
						DISCORD_ENABLED,
						DISCORD_WEBHOOK_URL,
						DISCORD_MINIMUM_LEVEL,
						SECURITY_EVENT_DISCORD_ENABLED,
						SECURITY_EVENT_MINIMUM_LEVEL,
						SECURITY_EVENT_AGGREGATION_WINDOW_SECONDS,
						RETENTION_DAYS,
						CREATED_DT,
						UPDATED_DT)
				.from(T_NOTIFICATION_GLOBAL_SETTINGS)
				.where(ID.eq(SINGLETON_ID))
				.fetchOptional(this::toDto);
	}

	/**
	 * 通知全体設定を保存する。
	 *
	 * @param request 保存内容
	 */
	public void save(NotificationGlobalSettingSaveRequest request) {
		LocalDateTime now = LocalDateTime.now(clock);
		String webhookUrl = StringUtils.hasText(request.discord_webhook_url())
				? request.discord_webhook_url().trim()
				: null;

		int updatedCount = dsl.update(T_NOTIFICATION_GLOBAL_SETTINGS)
				.set(ENABLED, request.enabled())
				.set(BROWSER_ENABLED, request.browser_enabled())
				.set(DISCORD_ENABLED, request.discord_enabled())
				.set(DISCORD_WEBHOOK_URL, webhookUrl)
				.set(DISCORD_MINIMUM_LEVEL, request.discord_minimum_level().getStoredName())
				.set(SECURITY_EVENT_DISCORD_ENABLED, request.security_event_discord_enabled())
				.set(SECURITY_EVENT_MINIMUM_LEVEL, request.security_event_minimum_level().getStoredName())
				.set(SECURITY_EVENT_AGGREGATION_WINDOW_SECONDS, request.security_event_aggregation_window_seconds())
				.set(RETENTION_DAYS, request.retention_days())
				.set(UPDATED_DT, now)
				.where(ID.eq(SINGLETON_ID))
				.execute();

		if (updatedCount > 0) {
			return;
		}

		dsl.insertInto(T_NOTIFICATION_GLOBAL_SETTINGS)
				.set(ID, SINGLETON_ID)
				.set(ENABLED, request.enabled())
				.set(BROWSER_ENABLED, request.browser_enabled())
				.set(DISCORD_ENABLED, request.discord_enabled())
				.set(DISCORD_WEBHOOK_URL, webhookUrl)
				.set(DISCORD_MINIMUM_LEVEL, request.discord_minimum_level().getStoredName())
				.set(SECURITY_EVENT_DISCORD_ENABLED, request.security_event_discord_enabled())
				.set(SECURITY_EVENT_MINIMUM_LEVEL, request.security_event_minimum_level().getStoredName())
				.set(SECURITY_EVENT_AGGREGATION_WINDOW_SECONDS, request.security_event_aggregation_window_seconds())
				.set(RETENTION_DAYS, request.retention_days())
				.set(CREATED_DT, now)
				.set(UPDATED_DT, now)
				.execute();
	}

	/**
	 * DBレコードをDTOへ変換する。
	 *
	 * @param record DBレコード
	 * @return 通知全体設定DTO
	 */
	private NotificationGlobalSettingDetailDto toDto(Record databaseRecord) {
		String webhookUrl = databaseRecord.get(DISCORD_WEBHOOK_URL);
		return NotificationGlobalSettingDetailDto.builder()
				.enabled(databaseRecord.get(ENABLED))
				.browserEnabled(databaseRecord.get(BROWSER_ENABLED))
				.discordEnabled(databaseRecord.get(DISCORD_ENABLED))
				.discordWebhookConfigured(StringUtils.hasText(webhookUrl))
				.discordWebhookUrl(webhookUrl)
				.discordMinimumLevel(NotificationLevel.from(databaseRecord.get(DISCORD_MINIMUM_LEVEL)))
				.securityEventDiscordEnabled(databaseRecord.get(SECURITY_EVENT_DISCORD_ENABLED))
				.securityEventMinimumLevel(NotificationLevel.from(databaseRecord.get(SECURITY_EVENT_MINIMUM_LEVEL)))
				.securityEventAggregationWindowSeconds(databaseRecord.get(SECURITY_EVENT_AGGREGATION_WINDOW_SECONDS))
				.retentionDays(databaseRecord.get(RETENTION_DAYS))
				.createdAt(databaseRecord.get(CREATED_DT))
				.updatedAt(databaseRecord.get(UPDATED_DT))
				.build();
	}
}
