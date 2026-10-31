package jp.xoki.web.zunda.smarthomewebserverbackend.common.repository;

import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.NotificationPageDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationCursor;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.pagination.Pagination;

import jp.xoki.web.zunda.jooq.tables.TNotifications;
import jp.xoki.web.zunda.jooq.tables.TUserNotifications;
import jp.xoki.web.zunda.jooq.tables.TNotificationRouteSettings;
import jp.xoki.web.zunda.jooq.tables.TUserNotificationPreferences;
import jp.xoki.web.zunda.jooq.tables.MNotificationRoles;
import jp.xoki.web.zunda.jooq.tables.TUserNotificationRoles;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.BrowserNotificationCreateDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.BrowserNotificationDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.NotificationRouteSettingDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.NotificationRouteSettingSaveRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.UserBrowserNotificationDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.UserNotificationPreferenceDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.UserNotificationPreferenceSaveDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.UserNotificationRouteSettingDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationDeliveryStatus;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationLevel;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationReadStatus;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationRoleCode;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationSourceType;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationTargetType;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationType;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Record3;
import org.jooq.Record6;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.NotificationRepositoryConstants.EMPTY_METADATA_JSON;
import static org.jooq.impl.DSL.lower;
import static org.jooq.impl.DSL.noCondition;
import java.time.Clock;

/** ブラウザ通知テーブルへアクセスするRepository。 */
@Repository
@RequiredArgsConstructor
public class NotificationRepository {

	/** ブラウザ通知本体テーブル。 */
	private static final TNotifications T_NOTIFICATIONS = TNotifications.T_NOTIFICATIONS;

	/** ユーザー別ブラウザ通知状態テーブル。 */
	private static final TUserNotifications T_USER_NOTIFICATIONS = TUserNotifications.T_USER_NOTIFICATIONS;

	/** 通知ルート設定テーブル。 */
	private static final TNotificationRouteSettings T_NOTIFICATION_ROUTE_SETTINGS = TNotificationRouteSettings.T_NOTIFICATION_ROUTE_SETTINGS;

	/** ユーザー別通知受信設定テーブル。 */
	private static final TUserNotificationPreferences T_USER_NOTIFICATION_PREFERENCES = TUserNotificationPreferences.T_USER_NOTIFICATION_PREFERENCES;

	/** 通知ロールマスタテーブル。 */
	private static final MNotificationRoles M_NOTIFICATION_ROLES = MNotificationRoles.M_NOTIFICATION_ROLES;

	/** ユーザー別通知ロール割当テーブル。 */
	private static final TUserNotificationRoles T_USER_NOTIFICATION_ROLES = TUserNotificationRoles.T_USER_NOTIFICATION_ROLES;

	/** 通知本体のDB内部ID。 */
	private static final Field<Long> NOTIFICATION_ROW_ID = T_NOTIFICATIONS.ID;

	/** 外部参照用通知ID。 */
	private static final Field<UUID> NOTIFICATION_ID = T_NOTIFICATIONS.NOTIFICATION_ID;

	/** ユーザー別通知状態登録用の外部参照用通知ID。 */
	private static final Field<UUID> USER_NOTIFICATION_ID = T_USER_NOTIFICATIONS.NOTIFICATION_ID;

	/** 通知本体側の外部参照用通知ID。 */
	private static final Field<UUID> NOTIFICATION_NOTIFICATION_ID =
			T_NOTIFICATIONS.NOTIFICATION_ID;

	/** ユーザー別通知状態側の外部参照用通知ID。 */
	private static final Field<UUID> USER_NOTIFICATION_NOTIFICATION_ID =
			T_USER_NOTIFICATIONS.NOTIFICATION_ID;

	/** 通知種別。 */
	private static final Field<String> NOTIFICATION_TYPE = T_NOTIFICATIONS.NOTIFICATION_TYPE;

	/** 通知レベル。 */
	private static final Field<String> LEVEL = T_NOTIFICATIONS.LEVEL;

	/** 通知タイトル。 */
	private static final Field<String> TITLE = T_NOTIFICATIONS.TITLE;

	/** 通知本文。 */
	private static final Field<String> MESSAGE = T_NOTIFICATIONS.MESSAGE;

	/** 通知発生元種別。 */
	private static final Field<String> SOURCE_TYPE = T_NOTIFICATIONS.SOURCE_TYPE;

	/** 発生元データID。 */
	private static final Field<String> SOURCE_ID = T_NOTIFICATIONS.SOURCE_ID;

	/** 通知クリック時の遷移先。 */
	private static final Field<String> LINK_PATH = T_NOTIFICATIONS.LINK_PATH;

	/** 補足情報JSON。 */
	private static final Field<JSONB> METADATA_JSON = T_NOTIFICATIONS.METADATA_JSON;

	/** 表示期限。 */
	private static final Field<LocalDateTime> EXPIRES_AT = T_NOTIFICATIONS.EXPIRES_AT;

	/** ユーザー別通知状態のDB内部ID。 */
	private static final Field<Long> USER_NOTIFICATION_ROW_ID =
			T_USER_NOTIFICATIONS.ID;

	/** 通知対象ユーザーID。 */
	private static final Field<Long> USER_ID = T_USER_NOTIFICATIONS.USER_ID;

	/** 配信状態。 */
	private static final Field<String> DELIVERY_STATUS = T_USER_NOTIFICATIONS.DELIVERY_STATUS;

	/** 既読状態。 */
	private static final Field<String> READ_STATUS = T_USER_NOTIFICATIONS.READ_STATUS;

	/** 配信日時。 */
	private static final Field<LocalDateTime> DELIVERED_AT = T_USER_NOTIFICATIONS.DELIVERED_AT;

	/** 既読日時。 */
	private static final Field<LocalDateTime> READ_AT = T_USER_NOTIFICATIONS.READ_AT;

	/** 非表示日時。 */
	private static final Field<LocalDateTime> DISMISSED_AT = T_USER_NOTIFICATIONS.DISMISSED_AT;

	/** 登録時に利用する作成日時。 */
	private static final Field<LocalDateTime> CREATED_DT = T_NOTIFICATIONS.CREATED_DT;

	/** 登録時に利用する更新日時。 */
	private static final Field<LocalDateTime> UPDATED_DT = T_NOTIFICATIONS.UPDATED_DT;

	/** 通知本体側の作成日時。 */
	private static final Field<LocalDateTime> NOTIFICATION_CREATED_DT =
			T_NOTIFICATIONS.CREATED_DT;

	/** 通知本体側の更新日時。 */
	private static final Field<LocalDateTime> NOTIFICATION_UPDATED_DT =
			T_NOTIFICATIONS.UPDATED_DT;

	/** ユーザー別通知状態側の作成日時。 */
	private static final Field<LocalDateTime> USER_NOTIFICATION_CREATED_DT =
			T_USER_NOTIFICATIONS.CREATED_DT;

	/** ユーザー別通知状態側の更新日時。 */
	private static final Field<LocalDateTime> USER_NOTIFICATION_UPDATED_DT =
			T_USER_NOTIFICATIONS.UPDATED_DT;

	/** 通知ルート設定側の通知種別。 */
	private static final Field<Long> ROUTE_ID =
			T_NOTIFICATION_ROUTE_SETTINGS.ID;

	/** 通知ルート設定側の通知種別。 */
	private static final Field<String> ROUTE_NOTIFICATION_TYPE =
			T_NOTIFICATION_ROUTE_SETTINGS.NOTIFICATION_TYPE;

	/** 通知ルート設定側の発生元種別。 */
	private static final Field<String> ROUTE_SOURCE_TYPE =
			T_NOTIFICATION_ROUTE_SETTINGS.SOURCE_TYPE;

	/** 通知ルート設定側の発生元データID。 */
	private static final Field<String> ROUTE_SOURCE_ID =
			T_NOTIFICATION_ROUTE_SETTINGS.SOURCE_ID;

	/** 通知ルート設定側の通知対象種別。 */
	private static final Field<String> ROUTE_TARGET_TYPE =
			T_NOTIFICATION_ROUTE_SETTINGS.TARGET_TYPE;

	/** 通知ルート設定側の通知対象ロールコード。 */
	private static final Field<String> ROUTE_TARGET_ROLE =
			T_NOTIFICATION_ROUTE_SETTINGS.TARGET_ROLE;

	/** 通知ルート設定側の通知対象ユーザーID。 */
	private static final Field<Long> ROUTE_TARGET_USER_ID =
			T_NOTIFICATION_ROUTE_SETTINGS.TARGET_USER_ID;

	/** 通知ルート設定側の最低通知レベル。 */
	private static final Field<String> ROUTE_MINIMUM_LEVEL =
			T_NOTIFICATION_ROUTE_SETTINGS.MINIMUM_LEVEL;

	/** 通知ルート設定側のブラウザ通知有効フラグ。 */
	private static final Field<Boolean> ROUTE_BROWSER_ENABLED =
			T_NOTIFICATION_ROUTE_SETTINGS.BROWSER_ENABLED;

	/** 通知ルート設定側のDiscord通知有効フラグ。 */
	private static final Field<Boolean> ROUTE_DISCORD_ENABLED =
			T_NOTIFICATION_ROUTE_SETTINGS.DISCORD_ENABLED;

	/** 通知ルート設定側の有効フラグ。 */
	private static final Field<Boolean> ROUTE_ENABLED =
			T_NOTIFICATION_ROUTE_SETTINGS.ENABLED;

	/** 通知ルート設定側の作成日時。 */
	private static final Field<LocalDateTime> ROUTE_CREATED_DT =
			T_NOTIFICATION_ROUTE_SETTINGS.CREATED_DT;

	/** 通知ルート設定側の更新日時。 */
	private static final Field<LocalDateTime> ROUTE_UPDATED_DT =
			T_NOTIFICATION_ROUTE_SETTINGS.UPDATED_DT;

	/** ユーザー別通知受信設定側のユーザーID。 */
	private static final Field<Long> PREFERENCE_USER_ID =
			T_USER_NOTIFICATION_PREFERENCES.USER_ID;

	/** ユーザー別通知受信設定側の通知種別。 */
	private static final Field<String> PREFERENCE_NOTIFICATION_TYPE =
			T_USER_NOTIFICATION_PREFERENCES.NOTIFICATION_TYPE;

	/** ユーザー別通知受信設定側の最低通知レベル。 */
	private static final Field<String> PREFERENCE_MINIMUM_LEVEL =
			T_USER_NOTIFICATION_PREFERENCES.MINIMUM_LEVEL;

	/** ユーザー別通知受信設定側のブラウザ通知有効フラグ。 */
	private static final Field<Boolean> PREFERENCE_BROWSER_ENABLED =
			T_USER_NOTIFICATION_PREFERENCES.BROWSER_ENABLED;

	/** ユーザー別通知受信設定側のDiscord通知有効フラグ。 */
	private static final Field<Boolean> PREFERENCE_DISCORD_ENABLED =
			T_USER_NOTIFICATION_PREFERENCES.DISCORD_ENABLED;

	/** ユーザー別通知受信設定側の有効フラグ。 */
	private static final Field<Boolean> PREFERENCE_ENABLED =
			T_USER_NOTIFICATION_PREFERENCES.ENABLED;

	/** ユーザー別通知受信設定側の作成日時。 */
	private static final Field<LocalDateTime> PREFERENCE_CREATED_DT =
			T_USER_NOTIFICATION_PREFERENCES.CREATED_DT;

	/** ユーザー別通知受信設定側の更新日時。 */
	private static final Field<LocalDateTime> PREFERENCE_UPDATED_DT =
			T_USER_NOTIFICATION_PREFERENCES.UPDATED_DT;

	/** 通知ロールマスタ側のDB内部ID。 */
	private static final Field<Long> NOTIFICATION_ROLE_ID =
			M_NOTIFICATION_ROLES.ID;

	/** 通知ロールマスタ側のロールコード。 */
	private static final Field<String> NOTIFICATION_ROLE_CODE =
			M_NOTIFICATION_ROLES.ROLE_CODE;

	/** 通知ロールマスタ側の有効フラグ。 */
	private static final Field<Boolean> NOTIFICATION_ROLE_ENABLED =
			M_NOTIFICATION_ROLES.ENABLED;

	/** ユーザー別通知ロール割当側のユーザーID。 */
	private static final Field<Long> USER_NOTIFICATION_ROLE_USER_ID =
			T_USER_NOTIFICATION_ROLES.USER_ID;

	/** ユーザー別通知ロール割当側の通知ロールID。 */
	private static final Field<Long> USER_NOTIFICATION_ROLE_ROLE_ID =
			T_USER_NOTIFICATION_ROLES.NOTIFICATION_ROLE_ID;

	/** ユーザー別通知ロール割当側の有効フラグ。 */
	private static final Field<Boolean> USER_NOTIFICATION_ROLE_ENABLED =
			T_USER_NOTIFICATION_ROLES.ENABLED;


	/** jOOQ実行コンテキスト。 */
	private final DSLContext dsl;

	private final Clock clock;

	/**
	 * ブラウザ通知本体を作成する。
	 *
	 * @param request 作成内容
	 * @return 作成された通知の外部参照用通知ID
	 */
	public UUID createNotification(BrowserNotificationCreateDto request) {
		LocalDateTime now = LocalDateTime.now(clock);

		return dsl.insertInto(T_NOTIFICATIONS)
				.set(NOTIFICATION_TYPE, request.getNotificationType().getStoredName())
				.set(LEVEL, request.getLevel().getStoredName())
				.set(TITLE, request.getTitle())
				.set(MESSAGE, request.getMessage())
				.set(SOURCE_TYPE, request.getSourceType().getStoredName())
				.set(SOURCE_ID, request.getSourceId())
				.set(LINK_PATH, request.getLinkPath())
				.set(METADATA_JSON, JSONB.valueOf(toMetadataJson(request.getMetadataJson())))
				.set(EXPIRES_AT, request.getExpiresAt())
				.set(CREATED_DT, now)
				.set(UPDATED_DT, now)
				.returningResult(NOTIFICATION_ID)
				.fetchOne(NOTIFICATION_ID);
	}

	/**
	 * ユーザー別通知状態を作成する。
	 *
	 * @param notificationId 外部参照用通知ID
	 * @param userId 通知対象ユーザーID
	 * @return 作成されたユーザー別通知状態ID
	 */
	public Long createUserNotification(UUID notificationId, Long userId) {
		LocalDateTime now = LocalDateTime.now(clock);

		return dsl.insertInto(T_USER_NOTIFICATIONS)
				.set(USER_NOTIFICATION_ID, notificationId)
				.set(USER_ID, userId)
				.set(DELIVERY_STATUS, NotificationDeliveryStatus.PENDING.getStoredName())
				.set(READ_STATUS, NotificationReadStatus.UNREAD.getStoredName())
				.set(T_USER_NOTIFICATIONS.CREATED_DT, now)
				.set(T_USER_NOTIFICATIONS.UPDATED_DT, now)
				.returningResult(T_USER_NOTIFICATIONS.ID)
				.fetchOne(T_USER_NOTIFICATIONS.ID);
	}

	/**
	 * 通知ルート設定とユーザー別受信設定からブラウザ通知対象ユーザーを解決する。
	 *
	 * <p>通知ルート設定では、発生元IDが一致する個別設定と発生元IDが未指定の全体設定を対象にする。
	 * 権限ロール指定はユーザーマスタから該当ユーザーへ展開し、最後にユーザー別受信設定で絞り込む。</p>
	 *
	 * @param request 通知作成内容
	 * @return ブラウザ通知対象ユーザーID一覧
	 */
	public List<Long> findRoutedBrowserUserIds(BrowserNotificationCreateDto request) {
		Set<Long> userIds = new LinkedHashSet<>();

		for (Record6<String, String, Long, String, Boolean, Boolean> route : findEnabledRoutes(request)) {
			if (!isRouteAllowed(route, request, ROUTE_BROWSER_ENABLED)) {
				continue;
			}
			addBrowserRouteUsers(userIds, route, request);
		}

		return List.copyOf(userIds);
	}

	/**
	 * 通知ルート設定とユーザー別受信設定からDiscord通知要否を判定する。
	 *
	 * <p>Discord Webhookはチャンネル通知のため、Discord有効なルートに対象ユーザーが1人以上残る場合に
	 * 通知対象とする。ユーザー別受信設定でDiscordを無効化しているユーザーは判定から除外する。</p>
	 *
	 * @param request 通知作成内容
	 * @return Discord通知対象の場合はtrue
	 */
	public boolean hasRoutedDiscordDestination(BrowserNotificationCreateDto request) {
		for (Record6<String, String, Long, String, Boolean, Boolean> route : findEnabledRoutes(request)) {
			if (!isRouteAllowed(route, request, ROUTE_DISCORD_ENABLED)) {
				continue;
			}
			if (hasDiscordRouteDestination(route, request)) {
				return true;
			}
		}

		return false;
	}

	private boolean isRouteAllowed(
			Record6<String, String, Long, String, Boolean, Boolean> route,
			BrowserNotificationCreateDto request,
			Field<Boolean> channelEnabledField) {
		return Boolean.TRUE.equals(route.get(channelEnabledField))
				&& allowsLevel(NotificationLevel.from(route.get(ROUTE_MINIMUM_LEVEL)), request.getLevel());
	}

	private void addBrowserRouteUsers(Set<Long> userIds,
			Record6<String, String, Long, String, Boolean, Boolean> route,
			BrowserNotificationCreateDto request) {
		NotificationTargetType targetType = NotificationTargetType.from(route.get(ROUTE_TARGET_TYPE));
		if (targetType == NotificationTargetType.USER) {
			addUserIfBrowserAllowed(userIds, route.get(ROUTE_TARGET_USER_ID), request);
			return;
		}
		if (targetType == NotificationTargetType.NOTIFICATION_ROLE) {
			for (Long userId : findUserIdsByNotificationRole(NotificationRoleCode.from(route.get(ROUTE_TARGET_ROLE)))) {
				addUserIfBrowserAllowed(userIds, userId, request);
			}
		}
	}

	private boolean hasDiscordRouteDestination(
			Record6<String, String, Long, String, Boolean, Boolean> route,
			BrowserNotificationCreateDto request) {
		NotificationTargetType targetType = NotificationTargetType.from(route.get(ROUTE_TARGET_TYPE));
		if (targetType == NotificationTargetType.USER) {
			return acceptsDiscordPreference(route.get(ROUTE_TARGET_USER_ID), request);
		}
		if (targetType == NotificationTargetType.NOTIFICATION_ROLE) {
			for (Long userId : findUserIdsByNotificationRole(NotificationRoleCode.from(route.get(ROUTE_TARGET_ROLE)))) {
				if (acceptsDiscordPreference(userId, request)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * 外部参照用通知IDに一致する通知本体を取得する。
	 *
	 * @param notificationId 外部参照用通知ID
	 * @return 通知本体
	 */
	public Optional<BrowserNotificationDetailDto> findNotification(UUID notificationId) {
		return dsl.selectFrom(T_NOTIFICATIONS)
				.where(NOTIFICATION_NOTIFICATION_ID.eq(notificationId))
				.fetchOptional(this::toNotificationDto);
	}

	/**
	 * ユーザー別の未読通知を作成日時の降順で取得する。
	 *
	 * @param userId 通知対象ユーザーID
	 * @param limit 最大取得件数
	 * @param offset 読み飛ばす件数
	 * @return ユーザー別通知一覧
	 */
	public List<UserBrowserNotificationDetailDto> findUnreadByUserId(
			Long userId, int limit, int offset, boolean canViewMachine, boolean canViewMaster) {
		return dsl.select()
				.from(T_USER_NOTIFICATIONS)
				.innerJoin(T_NOTIFICATIONS)
				.on(USER_NOTIFICATION_NOTIFICATION_ID.eq(NOTIFICATION_NOTIFICATION_ID))
				.where(USER_ID.eq(userId))
				.and(READ_STATUS.eq(NotificationReadStatus.UNREAD.getStoredName()))
				.and(EXPIRES_AT.isNull().or(EXPIRES_AT.gt(LocalDateTime.now(clock))))
				.and(visibleLinkCondition(canViewMachine, canViewMaster))
				.orderBy(USER_NOTIFICATION_CREATED_DT.desc(), USER_NOTIFICATION_ROW_ID.desc())
				.offset(offset)
				.limit(limit)
				.fetch(this::toUserNotificationDto);
	}

	/**
	 * ユーザー別の通知を作成日時の降順で取得する。
	 *
	 * @param userId 通知対象ユーザーID
	 * @param limit 最大取得件数
	 * @param offset 読み飛ばす件数
	 * @return ユーザー別通知一覧
	 */
	public List<UserBrowserNotificationDetailDto> findByUserId(
			Long userId, int limit, int offset, boolean canViewMachine, boolean canViewMaster) {
		return dsl.select()
				.from(T_USER_NOTIFICATIONS)
				.innerJoin(T_NOTIFICATIONS)
				.on(USER_NOTIFICATION_NOTIFICATION_ID.eq(NOTIFICATION_NOTIFICATION_ID))
				.where(USER_ID.eq(userId))
				.and(READ_STATUS.ne(NotificationReadStatus.DISMISSED.getStoredName()))
				.and(EXPIRES_AT.isNull().or(EXPIRES_AT.gt(LocalDateTime.now(clock))))
				.and(visibleLinkCondition(canViewMachine, canViewMaster))
				.orderBy(USER_NOTIFICATION_CREATED_DT.desc(), USER_NOTIFICATION_ROW_ID.desc())
				.offset(offset)
				.limit(limit)
				.fetch(this::toUserNotificationDto);
	}

	/** 新着追加・既読更新・削除で行位置がずれても、継続位置より古い通知を取得する。
	 *
	 * @param userId 通知対象ユーザー
	 * @param limit 最大件数
	 * @param cursor 前ページの継続位置
	 * @param unreadOnly 未読限定
	 * @param canViewMachine マシン閲覧権限
	 * @param canViewMaster マスタ閲覧権限
	 * @return 先読みと継続位置を含むページ
	 */
	public NotificationPageDto findPage(
			Long userId, int limit, NotificationCursor cursor,
			boolean unreadOnly, boolean canViewMachine, boolean canViewMaster) {
		Long maximumId = cursor == null
				? dsl.select(org.jooq.impl.DSL.max(USER_NOTIFICATION_ROW_ID)).from(T_USER_NOTIFICATIONS)
						.where(USER_ID.eq(userId)).fetchOne(0, Long.class)
				: cursor.snapshotId();
		if (maximumId == null) return new NotificationPageDto(List.of(), null);
		Condition position = cursor == null ? noCondition()
				: USER_NOTIFICATION_CREATED_DT.lt(cursor.createdAt()).or(
						USER_NOTIFICATION_CREATED_DT.eq(cursor.createdAt()).and(USER_NOTIFICATION_ROW_ID.lt(cursor.id())));
		List<UserBrowserNotificationDetailDto> rows = dsl.select().from(T_USER_NOTIFICATIONS)
				.innerJoin(T_NOTIFICATIONS).on(USER_NOTIFICATION_NOTIFICATION_ID.eq(NOTIFICATION_NOTIFICATION_ID))
				.where(USER_ID.eq(userId)).and(USER_NOTIFICATION_ROW_ID.le(maximumId)).and(position)
				.and(unreadOnly ? READ_STATUS.eq(NotificationReadStatus.UNREAD.getStoredName())
						: READ_STATUS.ne(NotificationReadStatus.DISMISSED.getStoredName()))
				.and(EXPIRES_AT.isNull().or(EXPIRES_AT.gt(LocalDateTime.now(clock))))
				.and(visibleLinkCondition(canViewMachine, canViewMaster))
				.orderBy(USER_NOTIFICATION_CREATED_DT.desc(), USER_NOTIFICATION_ROW_ID.desc()).limit(Pagination.lookAheadLimit(limit))
				.fetch(this::toUserNotificationDto);
		boolean hasMore = rows.size() > limit;
		List<UserBrowserNotificationDetailDto> items = rows.subList(0, Math.min(rows.size(), limit));
		UserBrowserNotificationDetailDto last = items.isEmpty() ? null : items.getLast();
		String next = hasMore && last != null
				? new NotificationCursor(
						last.getCreatedAt(), last.getId(), maximumId).encode() : null;
		return new NotificationPageDto(items, next);
	}

	/**
	 * ユーザー別通知受信設定を取得する。
	 *
	 * @param userId 通知対象ユーザーID
	 * @return ユーザー別通知受信設定一覧
	 */
	public List<UserNotificationPreferenceDetailDto> findPreferencesByUserId(Long userId) {
		return dsl.select(
						PREFERENCE_USER_ID,
						PREFERENCE_NOTIFICATION_TYPE,
						PREFERENCE_MINIMUM_LEVEL,
						PREFERENCE_BROWSER_ENABLED,
						PREFERENCE_DISCORD_ENABLED,
						PREFERENCE_ENABLED,
						PREFERENCE_CREATED_DT,
						PREFERENCE_UPDATED_DT)
				.from(T_USER_NOTIFICATION_PREFERENCES)
				.where(PREFERENCE_USER_ID.eq(userId))
				.orderBy(PREFERENCE_NOTIFICATION_TYPE.asc())
				.fetch(databaseRecord -> UserNotificationPreferenceDetailDto.builder()
						.userId(databaseRecord.get(PREFERENCE_USER_ID))
						.notificationType(NotificationType.from(databaseRecord.get(PREFERENCE_NOTIFICATION_TYPE)))
						.minimumLevel(NotificationLevel.from(databaseRecord.get(PREFERENCE_MINIMUM_LEVEL)))
						.browserEnabled(databaseRecord.get(PREFERENCE_BROWSER_ENABLED))
						.discordEnabled(databaseRecord.get(PREFERENCE_DISCORD_ENABLED))
						.enabled(databaseRecord.get(PREFERENCE_ENABLED))
						.createdAt(databaseRecord.get(PREFERENCE_CREATED_DT, LocalDateTime.class))
						.updatedAt(databaseRecord.get(PREFERENCE_UPDATED_DT, LocalDateTime.class))
						.build());
	}

	/**
	 * ユーザー別通知受信設定を登録または更新する。
	 *
	 * @param userId 通知対象ユーザーID
	 * @param preference 保存内容
	 */
	public void savePreference(Long userId, UserNotificationPreferenceSaveDto preference) {
		LocalDateTime now = LocalDateTime.now(clock);
		String notificationType = preference.notification_type().getStoredName();

		int updated = dsl.update(T_USER_NOTIFICATION_PREFERENCES)
				.set(PREFERENCE_MINIMUM_LEVEL, preference.minimum_level().getStoredName())
				.set(PREFERENCE_BROWSER_ENABLED, preference.browser_enabled())
				.set(PREFERENCE_DISCORD_ENABLED, preference.discord_enabled())
				.set(PREFERENCE_ENABLED, preference.enabled())
				.set(PREFERENCE_UPDATED_DT, now)
				.where(PREFERENCE_USER_ID.eq(userId))
				.and(PREFERENCE_NOTIFICATION_TYPE.eq(notificationType))
				.execute();

		if (updated > 0) {
			return;
		}

		dsl.insertInto(T_USER_NOTIFICATION_PREFERENCES)
				.set(PREFERENCE_USER_ID, userId)
				.set(PREFERENCE_NOTIFICATION_TYPE, notificationType)
				.set(PREFERENCE_MINIMUM_LEVEL, preference.minimum_level().getStoredName())
				.set(PREFERENCE_BROWSER_ENABLED, preference.browser_enabled())
				.set(PREFERENCE_DISCORD_ENABLED, preference.discord_enabled())
				.set(PREFERENCE_ENABLED, preference.enabled())
				.set(T_USER_NOTIFICATION_PREFERENCES.CREATED_DT, now)
				.set(T_USER_NOTIFICATION_PREFERENCES.UPDATED_DT, now)
				.execute();
	}

	/**
	 * 個別ユーザー宛て通知ルート設定を取得する。
	 *
	 * @param userId 通知対象ユーザーID
	 * @return 個別ユーザー宛て通知ルート設定一覧
	 */
	public List<UserNotificationRouteSettingDetailDto> findRouteSettingsByTargetUserId(Long userId) {
		return dsl.select(
						ROUTE_ID,
						ROUTE_NOTIFICATION_TYPE,
						ROUTE_SOURCE_TYPE,
						ROUTE_SOURCE_ID,
						ROUTE_TARGET_USER_ID,
						ROUTE_MINIMUM_LEVEL,
						ROUTE_BROWSER_ENABLED,
						ROUTE_DISCORD_ENABLED,
						ROUTE_ENABLED,
						ROUTE_CREATED_DT,
						ROUTE_UPDATED_DT)
				.from(T_NOTIFICATION_ROUTE_SETTINGS)
				.where(ROUTE_TARGET_TYPE.eq(NotificationTargetType.USER.getStoredName()))
				.and(ROUTE_TARGET_USER_ID.eq(userId))
				.orderBy(ROUTE_NOTIFICATION_TYPE.asc(), ROUTE_SOURCE_TYPE.asc(), ROUTE_ID.asc())
				.fetch(databaseRecord -> UserNotificationRouteSettingDetailDto.builder()
						.id(databaseRecord.get(ROUTE_ID))
						.notificationType(NotificationType.from(databaseRecord.get(ROUTE_NOTIFICATION_TYPE)))
						.sourceType(NotificationSourceType.from(databaseRecord.get(ROUTE_SOURCE_TYPE)))
						.sourceId(databaseRecord.get(ROUTE_SOURCE_ID))
						.targetUserId(databaseRecord.get(ROUTE_TARGET_USER_ID))
						.minimumLevel(NotificationLevel.from(databaseRecord.get(ROUTE_MINIMUM_LEVEL)))
						.browserEnabled(databaseRecord.get(ROUTE_BROWSER_ENABLED))
						.discordEnabled(databaseRecord.get(ROUTE_DISCORD_ENABLED))
						.enabled(databaseRecord.get(ROUTE_ENABLED))
						.createdAt(databaseRecord.get(ROUTE_CREATED_DT, LocalDateTime.class))
						.updatedAt(databaseRecord.get(ROUTE_UPDATED_DT, LocalDateTime.class))
						.build());
	}

	/**
	 * 通知ルート設定一覧を取得する。
	 *
	 * @return 通知ルート設定一覧
	 */
	public List<NotificationRouteSettingDetailDto> findRouteSettings() {
		return dsl.select(
						ROUTE_ID,
						ROUTE_NOTIFICATION_TYPE,
						ROUTE_SOURCE_TYPE,
						ROUTE_SOURCE_ID,
						ROUTE_TARGET_TYPE,
						ROUTE_TARGET_ROLE,
						ROUTE_TARGET_USER_ID,
						ROUTE_MINIMUM_LEVEL,
						ROUTE_BROWSER_ENABLED,
						ROUTE_DISCORD_ENABLED,
						ROUTE_ENABLED,
						ROUTE_CREATED_DT,
						ROUTE_UPDATED_DT)
				.from(T_NOTIFICATION_ROUTE_SETTINGS)
				.orderBy(ROUTE_NOTIFICATION_TYPE.asc(), ROUTE_SOURCE_TYPE.asc(), ROUTE_ID.asc())
				.fetch(this::toRouteSettingDto);
	}

	/**
	 * 通知ルート設定をID指定で取得する。
	 *
	 * @param id 通知ルート設定ID
	 * @return 通知ルート設定
	 */
	public Optional<NotificationRouteSettingDetailDto> findRouteSettingById(Long id) {
		return dsl.select(
						ROUTE_ID,
						ROUTE_NOTIFICATION_TYPE,
						ROUTE_SOURCE_TYPE,
						ROUTE_SOURCE_ID,
						ROUTE_TARGET_TYPE,
						ROUTE_TARGET_ROLE,
						ROUTE_TARGET_USER_ID,
						ROUTE_MINIMUM_LEVEL,
						ROUTE_BROWSER_ENABLED,
						ROUTE_DISCORD_ENABLED,
						ROUTE_ENABLED,
						ROUTE_CREATED_DT,
						ROUTE_UPDATED_DT)
				.from(T_NOTIFICATION_ROUTE_SETTINGS)
				.where(ROUTE_ID.eq(id))
				.fetchOptional(this::toRouteSettingDto);
	}

	/**
	 * 有効な通知ロールが存在するか確認する。
	 *
	 * @param roleCode 通知ロールコード
	 * @return 有効な通知ロールが存在する場合はtrue
	 */
	public boolean existsEnabledNotificationRole(String roleCode) {
		return dsl.fetchExists(
				dsl.selectOne()
						.from(M_NOTIFICATION_ROLES)
						.where(NOTIFICATION_ROLE_CODE.eq(roleCode))
						.and(NOTIFICATION_ROLE_ENABLED.eq(true)));
	}

	/**
	 * 通知ルート設定を登録または更新する。
	 *
	 * @param request 保存内容
	 * @return 保存後の通知ルート設定ID
	 */
	public Long saveRouteSetting(NotificationRouteSettingSaveRequest request) {
		LocalDateTime now = LocalDateTime.now(clock);
		String sourceId = StringUtils.hasText(request.source_id()) ? request.source_id() : null;
		String targetRole = request.target_type() == NotificationTargetType.NOTIFICATION_ROLE
				? request.target_role()
				: null;
		Long targetUserId = request.target_type() == NotificationTargetType.USER
				? request.target_user_id()
				: null;

		if (request.id() == null) {
			return dsl.insertInto(T_NOTIFICATION_ROUTE_SETTINGS)
					.set(ROUTE_NOTIFICATION_TYPE, request.notification_type().getStoredName())
					.set(ROUTE_SOURCE_TYPE, request.source_type().getStoredName())
					.set(ROUTE_SOURCE_ID, sourceId)
					.set(ROUTE_TARGET_TYPE, request.target_type().getStoredName())
					.set(ROUTE_TARGET_ROLE, targetRole)
					.set(ROUTE_TARGET_USER_ID, targetUserId)
					.set(ROUTE_MINIMUM_LEVEL, request.minimum_level().getStoredName())
					.set(ROUTE_BROWSER_ENABLED, request.browser_enabled())
					.set(ROUTE_DISCORD_ENABLED, request.discord_enabled())
					.set(ROUTE_ENABLED, request.enabled())
					.set(T_NOTIFICATION_ROUTE_SETTINGS.CREATED_DT, now)
					.set(T_NOTIFICATION_ROUTE_SETTINGS.UPDATED_DT, now)
					.returningResult(T_NOTIFICATION_ROUTE_SETTINGS.ID)
					.fetchOne(T_NOTIFICATION_ROUTE_SETTINGS.ID);
		}

		dsl.update(T_NOTIFICATION_ROUTE_SETTINGS)
				.set(ROUTE_NOTIFICATION_TYPE, request.notification_type().getStoredName())
				.set(ROUTE_SOURCE_TYPE, request.source_type().getStoredName())
				.set(ROUTE_SOURCE_ID, sourceId)
				.set(ROUTE_TARGET_TYPE, request.target_type().getStoredName())
				.set(ROUTE_TARGET_ROLE, targetRole)
				.set(ROUTE_TARGET_USER_ID, targetUserId)
				.set(ROUTE_MINIMUM_LEVEL, request.minimum_level().getStoredName())
				.set(ROUTE_BROWSER_ENABLED, request.browser_enabled())
				.set(ROUTE_DISCORD_ENABLED, request.discord_enabled())
				.set(ROUTE_ENABLED, request.enabled())
				.set(ROUTE_UPDATED_DT, now)
				.where(ROUTE_ID.eq(request.id()))
				.execute();

		return request.id();
	}

	/**
	 * ユーザー別の未読通知件数を取得する。
	 *
	 * @param userId 通知対象ユーザーID
	 * @return 未読通知件数
	 */
	public int countUnreadByUserId(Long userId, boolean canViewMachine, boolean canViewMaster) {
		Integer count = dsl.selectCount()
				.from(T_USER_NOTIFICATIONS)
				.innerJoin(T_NOTIFICATIONS)
				.on(USER_NOTIFICATION_NOTIFICATION_ID.eq(NOTIFICATION_NOTIFICATION_ID))
				.where(USER_ID.eq(userId))
				.and(READ_STATUS.eq(NotificationReadStatus.UNREAD.getStoredName()))
				.and(EXPIRES_AT.isNull().or(EXPIRES_AT.gt(LocalDateTime.now(clock))))
				.and(visibleLinkCondition(canViewMachine, canViewMaster))
				.fetchSingle(0, Integer.class);
		return java.util.Objects.requireNonNull(count, "Notification count was null");
	}

	private Condition visibleLinkCondition(boolean canViewMachine, boolean canViewMaster) {
		Condition condition = noCondition();
		if (!canViewMachine) {
			condition = condition.and(LINK_PATH.isNull()
					.or(lower(LINK_PATH).notLike("/machine/status%")));
		}
		if (!canViewMaster) {
			condition = condition.and(LINK_PATH.isNull()
					.or(lower(LINK_PATH).notLike("/master%")))
					.and(SOURCE_TYPE.ne(NotificationSourceType.BATCH_EXECUTION.getStoredName()));
		}
		return condition;
	}

	/**
	 * ユーザー別通知を配信済みに更新する。
	 *
	 * @param notificationId 外部参照用通知ID
	 * @param userId 通知対象ユーザーID
	 * @return 更新件数
	 */
	public int markDelivered(UUID notificationId, Long userId) {
		LocalDateTime now = LocalDateTime.now(clock);

		return dsl.update(T_USER_NOTIFICATIONS)
				.set(DELIVERY_STATUS, NotificationDeliveryStatus.DELIVERED.getStoredName())
				.set(DELIVERED_AT, now)
				.set(USER_NOTIFICATION_UPDATED_DT, now)
				.where(USER_NOTIFICATION_NOTIFICATION_ID.eq(notificationId))
				.and(USER_ID.eq(userId))
				.execute();
	}

	/**
	 * ユーザー別通知を既読に更新する。
	 *
	 * @param notificationId 外部参照用通知ID
	 * @param userId 通知対象ユーザーID
	 * @return 更新件数
	 */
	public int markRead(UUID notificationId, Long userId) {
		LocalDateTime now = LocalDateTime.now(clock);

		return dsl.update(T_USER_NOTIFICATIONS)
				.set(READ_STATUS, NotificationReadStatus.READ.getStoredName())
				.set(READ_AT, now)
				.set(USER_NOTIFICATION_UPDATED_DT, now)
				.where(USER_NOTIFICATION_NOTIFICATION_ID.eq(notificationId))
				.and(USER_ID.eq(userId))
				.execute();
	}

	/**
	 * ユーザー別通知を非表示に更新する。
	 *
	 * @param notificationId 外部参照用通知ID
	 * @param userId 通知対象ユーザーID
	 * @return 更新件数
	 */
	public int dismiss(UUID notificationId, Long userId) {
		LocalDateTime now = LocalDateTime.now(clock);

		return dsl.update(T_USER_NOTIFICATIONS)
				.set(READ_STATUS, NotificationReadStatus.DISMISSED.getStoredName())
				.set(DISMISSED_AT, now)
				.set(USER_NOTIFICATION_UPDATED_DT, now)
				.where(USER_NOTIFICATION_NOTIFICATION_ID.eq(notificationId))
				.and(USER_ID.eq(userId))
				.execute();
	}

	/**
	 * 通知内容に一致する有効な通知ルート設定を取得する。
	 *
	 * @param request 通知作成内容
	 * @return 有効な通知ルート設定一覧
	 */
	private List<Record6<String, String, Long, String, Boolean, Boolean>> findEnabledRoutes(
			BrowserNotificationCreateDto request
	) {
		return dsl.select(
						ROUTE_TARGET_TYPE,
						ROUTE_TARGET_ROLE,
						ROUTE_TARGET_USER_ID,
						ROUTE_MINIMUM_LEVEL,
						ROUTE_BROWSER_ENABLED,
						ROUTE_DISCORD_ENABLED)
				.from(T_NOTIFICATION_ROUTE_SETTINGS)
				.where(ROUTE_ENABLED.eq(true))
				.and(ROUTE_NOTIFICATION_TYPE.eq(request.getNotificationType().getStoredName()))
				.and(ROUTE_SOURCE_TYPE.eq(request.getSourceType().getStoredName()))
				.and(sourceIdCondition(request.getSourceId()))
				.fetch();
	}

	/**
	 * 発生元IDに対応する通知ルート検索条件を生成する。
	 *
	 * @param sourceId 発生元データID
	 * @return 発生元IDの検索条件
	 */
	private Condition sourceIdCondition(String sourceId) {

		// 発生元IDが未指定の場合は、全体設定のみを対象にする。
		if (!StringUtils.hasText(sourceId)) {
			return ROUTE_SOURCE_ID.isNull();
		}

		return ROUTE_SOURCE_ID.isNull().or(ROUTE_SOURCE_ID.eq(sourceId));
	}

	/**
	 * 通知ロールに紐づくユーザーID一覧を取得する。
	 *
	 * @param roleCode 通知ロールコード
	 * @return 該当通知ロールを持つユーザーID一覧
	 */
	private List<Long> findUserIdsByNotificationRole(NotificationRoleCode roleCode) {

		// 通知ロールコードが未指定の場合は通知対象を解決しない。
		if (roleCode == null) {
			return List.of();
		}

		return dsl.select(USER_NOTIFICATION_ROLE_USER_ID)
				.from(T_USER_NOTIFICATION_ROLES)
				.innerJoin(M_NOTIFICATION_ROLES)
				.on(USER_NOTIFICATION_ROLE_ROLE_ID.eq(NOTIFICATION_ROLE_ID))
				.where(NOTIFICATION_ROLE_CODE.eq(roleCode.getStoredName()))
				.and(NOTIFICATION_ROLE_ENABLED.eq(true))
				.and(USER_NOTIFICATION_ROLE_ENABLED.eq(true))
				.fetch(USER_NOTIFICATION_ROLE_USER_ID);
	}

	/**
	 * ブラウザ通知を受信可能なユーザーだけを宛先候補へ追加する。
	 *
	 * @param userIds 宛先候補ユーザーID一覧
	 * @param userId 追加候補ユーザーID
	 * @param request 通知作成内容
	 */
	private void addUserIfBrowserAllowed(Set<Long> userIds, Long userId, BrowserNotificationCreateDto request) {

		// 通知対象ユーザーが未指定の場合は宛先へ追加しない。
		if (userId == null) {
			return;
		}

		// ユーザー別受信設定でブラウザ通知が許可されている場合のみ宛先へ追加する。
		if (acceptsBrowserPreference(userId, request)) {
			userIds.add(userId);
		}
	}

	/**
	 * ユーザー別受信設定からブラウザ通知の受信可否を判定する。
	 *
	 * @param userId 通知対象ユーザーID
	 * @param request 通知作成内容
	 * @return ブラウザ通知を受信する場合はtrue
	 */
	private boolean acceptsBrowserPreference(Long userId, BrowserNotificationCreateDto request) {
		return acceptsPreference(userId, request, PREFERENCE_BROWSER_ENABLED);
	}

	/**
	 * ユーザー別受信設定からDiscord通知の受信可否を判定する。
	 *
	 * @param userId 通知対象ユーザーID
	 * @param request 通知作成内容
	 * @return Discord通知を受信する場合はtrue
	 */
	private boolean acceptsDiscordPreference(Long userId, BrowserNotificationCreateDto request) {
		return acceptsPreference(userId, request, PREFERENCE_DISCORD_ENABLED);
	}

	/**
	 * ユーザー別受信設定から指定チャネルの受信可否を判定する。
	 *
	 * @param userId 通知対象ユーザーID
	 * @param request 通知作成内容
	 * @param channelEnabledField 判定する通知チャネルの有効フラグ
	 * @return 指定チャネルで受信する場合はtrue
	 */
	private boolean acceptsPreference(
			Long userId,
			BrowserNotificationCreateDto request,
			Field<Boolean> channelEnabledField
	) {
		Optional<Record3<Boolean, Boolean, String>> preference = dsl.select(
						PREFERENCE_ENABLED,
						channelEnabledField,
						PREFERENCE_MINIMUM_LEVEL)
				.from(T_USER_NOTIFICATION_PREFERENCES)
				.where(PREFERENCE_USER_ID.eq(userId))
				.and(PREFERENCE_NOTIFICATION_TYPE.eq(request.getNotificationType().getStoredName()))
				.fetchOptional();

		// ユーザー別受信設定が未作成の場合は、通知ルート設定をそのまま採用する。
		if (preference.isEmpty()) {
			return true;
		}

		Record3<Boolean, Boolean, String> databaseRecord = preference.get();

		// ユーザー別受信設定が無効、または指定チャネルが無効な場合は通知しない。
		if (!Boolean.TRUE.equals(databaseRecord.get(PREFERENCE_ENABLED))
				|| !Boolean.TRUE.equals(databaseRecord.get(channelEnabledField))) {
			return false;
		}

		return allowsLevel(NotificationLevel.from(databaseRecord.get(PREFERENCE_MINIMUM_LEVEL)), request.getLevel());
	}

	/**
	 * 実際の通知レベルが最低通知レベルを満たしているかを判定する。
	 *
	 * @param minimumLevel 最低通知レベル
	 * @param actualLevel 実際の通知レベル
	 * @return 実際の通知レベルが最低通知レベル以上の場合はtrue
	 */
	private boolean allowsLevel(NotificationLevel minimumLevel, NotificationLevel actualLevel) {

		// レベル未指定の場合は安全側に倒し、通知対象外として扱う。
		if (minimumLevel == null || actualLevel == null) {
			return false;
		}

		return actualLevel.ordinal() >= minimumLevel.ordinal();
	}

	/**
	 * 補足情報JSON文字列をDB保存用に補正する。
	 *
	 * @param metadataJson 補足情報JSON文字列
	 * @return DBへ保存する補足情報JSON文字列
	 */
	private String toMetadataJson(String metadataJson) {

		// 補足情報が未指定の場合は空のJSONオブジェクトとして保存する。
		if (!StringUtils.hasText(metadataJson)) {
			return EMPTY_METADATA_JSON;
		}

		return metadataJson;
	}

	/**
	 * 通知本体レコードをDTOへ変換する。
	 *
	 * @param record 通知本体レコード
	 * @return 通知本体DTO
	 */
	private BrowserNotificationDetailDto toNotificationDto(Record databaseRecord) {
		return BrowserNotificationDetailDto.builder()
				.id(databaseRecord.get(NOTIFICATION_ROW_ID))
				.notificationId(databaseRecord.get(NOTIFICATION_NOTIFICATION_ID))
				.notificationType(NotificationType.from(databaseRecord.get(NOTIFICATION_TYPE)))
				.level(NotificationLevel.from(databaseRecord.get(LEVEL)))
				.title(databaseRecord.get(TITLE))
				.message(databaseRecord.get(MESSAGE))
				.sourceType(NotificationSourceType.from(databaseRecord.get(SOURCE_TYPE)))
				.sourceId(databaseRecord.get(SOURCE_ID))
				.linkPath(databaseRecord.get(LINK_PATH))
				.metadataJson(databaseRecord.get(METADATA_JSON).data())
				.expiresAt(databaseRecord.get(EXPIRES_AT, LocalDateTime.class))
				.createdAt(databaseRecord.get(NOTIFICATION_CREATED_DT, LocalDateTime.class))
				.updatedAt(databaseRecord.get(NOTIFICATION_UPDATED_DT, LocalDateTime.class))
				.build();
	}

	/**
	 * ユーザー別通知状態を含むレコードをDTOへ変換する。
	 *
	 * @param record ユーザー別通知状態を含むレコード
	 * @return ユーザー別通知状態DTO
	 */
	private UserBrowserNotificationDetailDto toUserNotificationDto(Record databaseRecord) {
		return UserBrowserNotificationDetailDto.builder()
				.id(databaseRecord.get(USER_NOTIFICATION_ROW_ID))
				.notificationId(databaseRecord.get(NOTIFICATION_NOTIFICATION_ID))
				.userId(databaseRecord.get(USER_ID))
				.deliveryStatus(NotificationDeliveryStatus.from(databaseRecord.get(DELIVERY_STATUS)))
				.readStatus(NotificationReadStatus.from(databaseRecord.get(READ_STATUS)))
				.deliveredAt(databaseRecord.get(DELIVERED_AT, LocalDateTime.class))
				.readAt(databaseRecord.get(READ_AT, LocalDateTime.class))
				.dismissedAt(databaseRecord.get(DISMISSED_AT, LocalDateTime.class))
				.notificationType(NotificationType.from(databaseRecord.get(NOTIFICATION_TYPE)))
				.level(NotificationLevel.from(databaseRecord.get(LEVEL)))
				.title(databaseRecord.get(TITLE))
				.message(databaseRecord.get(MESSAGE))
				.sourceType(NotificationSourceType.from(databaseRecord.get(SOURCE_TYPE)))
				.sourceId(databaseRecord.get(SOURCE_ID))
				.linkPath(databaseRecord.get(LINK_PATH))
				.metadataJson(databaseRecord.get(METADATA_JSON).data())
				.expiresAt(databaseRecord.get(EXPIRES_AT, LocalDateTime.class))
				.createdAt(databaseRecord.get(USER_NOTIFICATION_CREATED_DT, LocalDateTime.class))
				.updatedAt(databaseRecord.get(USER_NOTIFICATION_UPDATED_DT, LocalDateTime.class))
				.build();
	}

	/**
	 * 通知ルート設定レコードをDTOへ変換する。
	 *
	 * @param record 通知ルート設定レコード
	 * @return 通知ルート設定DTO
	 */
	private NotificationRouteSettingDetailDto toRouteSettingDto(Record databaseRecord) {
		return NotificationRouteSettingDetailDto.builder()
				.id(databaseRecord.get(ROUTE_ID))
				.notificationType(NotificationType.from(databaseRecord.get(ROUTE_NOTIFICATION_TYPE)))
				.sourceType(NotificationSourceType.from(databaseRecord.get(ROUTE_SOURCE_TYPE)))
				.sourceId(databaseRecord.get(ROUTE_SOURCE_ID))
				.targetType(NotificationTargetType.from(databaseRecord.get(ROUTE_TARGET_TYPE)))
				.targetRole(databaseRecord.get(ROUTE_TARGET_ROLE))
				.targetUserId(databaseRecord.get(ROUTE_TARGET_USER_ID))
				.minimumLevel(NotificationLevel.from(databaseRecord.get(ROUTE_MINIMUM_LEVEL)))
				.browserEnabled(databaseRecord.get(ROUTE_BROWSER_ENABLED))
				.discordEnabled(databaseRecord.get(ROUTE_DISCORD_ENABLED))
				.enabled(databaseRecord.get(ROUTE_ENABLED))
				.createdAt(databaseRecord.get(ROUTE_CREATED_DT, LocalDateTime.class))
				.updatedAt(databaseRecord.get(ROUTE_UPDATED_DT, LocalDateTime.class))
				.build();
	}
}
