package jp.xoki.web.zunda.smarthomewebserverbackend.common.repository;

import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.BrowserNotificationCreateDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.NotificationRouteSettingDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.NotificationRouteSettingSaveRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.UserBrowserNotificationDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.UserNotificationPreferenceSaveDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.UserNotificationRouteSettingDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationDeliveryStatus;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationLevel;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationReadStatus;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationRoleCode;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationSourceType;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationTargetType;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.BrowserNotificationDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.UserNotificationPreferenceDetailDto;

@SpringBootTest(properties = {"mqtt.enabled=false", "app.agent-mqtt.enabled=false",
        "notification.discord.enabled=false", "notification.discord.security-events.enabled=false",
        "spring.batch.job.enabled=false", "app.vehicle-inspection-notification.enabled=false"})
@Transactional
class NotificationRepositoryIntegrationTest {
    @Autowired NotificationRepository repository;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void isolateSystemNoticeRoutes() {
        jdbc.update("delete from t_notification_route_settings where notification_type='system_notice'");
    }

    @Test
    void userRoutesPreserveAllSettingsAndAppearInTheRecipientsPersonalRoutes() {
        long userId = user();
        NotificationRouteSettingSaveRequest input = route(userId, "  ", NotificationLevel.WARNING, true, false, true);
        Long id = repository.saveRouteSetting(input);
        NotificationRouteSettingDetailDto created = repository.findRouteSettingById(id).orElseThrow();
        assertThat(created.getTargetType()).isEqualTo(NotificationTargetType.USER);
        assertThat(created.getTargetUserId()).isEqualTo(userId);
        assertThat(created.getTargetRole()).isNull();
        assertThat(created.getSourceId()).isNull();
        assertThat(created.getNotificationType()).isEqualTo(NotificationType.SYSTEM_NOTICE);
        assertThat(created.getSourceType()).isEqualTo(NotificationSourceType.SYSTEM);
        assertThat(created.getMinimumLevel()).isEqualTo(NotificationLevel.WARNING);
        assertThat(created.getBrowserEnabled()).isTrue();
        assertThat(created.getDiscordEnabled()).isFalse();
        assertThat(created.getEnabled()).isTrue();
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(created.getUpdatedAt()).isNotNull();
        assertThat(repository.findRouteSettings()).extracting(NotificationRouteSettingDetailDto::getId).contains(id);
        List<UserNotificationRouteSettingDetailDto> personal = repository.findRouteSettingsByTargetUserId(userId);
        assertThat(personal).extracting(UserNotificationRouteSettingDetailDto::getId).containsExactly(id);
        assertThat(personal.getFirst().getTargetUserId()).isEqualTo(userId);
        assertThat(personal.getFirst().getMinimumLevel()).isEqualTo(NotificationLevel.WARNING);
        assertThat(personal.getFirst().getCreatedAt()).isEqualTo(created.getCreatedAt());
    }

    @Test
    void routesCanSwitchToNotificationRolesWithoutKeepingTheOldUserTarget() {
        long userId = user();
        Long id = repository.saveRouteSetting(route(userId, null, NotificationLevel.WARNING, true, false, true));
        NotificationRouteSettingDetailDto created = repository.findRouteSettingById(id).orElseThrow();
        NotificationRouteSettingSaveRequest role = new NotificationRouteSettingSaveRequest(id, NotificationType.SYSTEM_NOTICE,
                NotificationSourceType.SYSTEM, "node-1", NotificationTargetType.NOTIFICATION_ROLE,
                NotificationRoleCode.ITEM_STOCK.getStoredName(), userId,
                NotificationLevel.CRITICAL, false, true, false);
        assertThat(repository.saveRouteSetting(role)).isEqualTo(id);
        NotificationRouteSettingDetailDto updated = repository.findRouteSettingById(id).orElseThrow();
        assertThat(updated.getTargetType()).isEqualTo(NotificationTargetType.NOTIFICATION_ROLE);
        assertThat(updated.getTargetUserId()).isNull();
        assertThat(updated.getTargetRole()).isEqualTo(NotificationRoleCode.ITEM_STOCK.getStoredName());
        assertThat(updated.getSourceId()).isEqualTo("node-1");
        assertThat(updated.getMinimumLevel()).isEqualTo(NotificationLevel.CRITICAL);
        assertThat(updated.getBrowserEnabled()).isFalse();
        assertThat(updated.getDiscordEnabled()).isTrue();
        assertThat(updated.getEnabled()).isFalse();
        assertThat(updated.getCreatedAt()).isEqualTo(created.getCreatedAt());
        assertThat(repository.findRouteSettingsByTargetUserId(userId)).isEmpty();
        assertThat(repository.findRouteSettingById(Long.MAX_VALUE)).isEmpty();
        assertThat(repository.existsEnabledNotificationRole(NotificationRoleCode.ITEM_STOCK.getStoredName())).isTrue();
        assertThat(repository.existsEnabledNotificationRole("UNKNOWN_ROLE")).isFalse();
        jdbc.update("update m_notification_roles set enabled=false where role_code=?", role.target_role());
        assertThat(repository.existsEnabledNotificationRole(role.target_role())).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "true,true,true,INFO,true,true", "true,false,true,WARNING,false,true",
            "true,true,false,WARNING,true,false", "false,true,true,INFO,false,false",
            "true,true,true,CRITICAL,false,false"
    })
    void preferencesApplyPerChannelAndLevelAndCanBeUpdated(
            boolean enabled, boolean browser, boolean discord, NotificationLevel minimum,
            boolean expectedBrowser, boolean expectedDiscord) {
        long userId = user();
        repository.saveRouteSetting(route(userId, null, NotificationLevel.INFO, true, true, true));
        BrowserNotificationCreateDto notice = notice("node-1", NotificationLevel.WARNING, null, null);
        assertThat(repository.findRoutedBrowserUserIds(notice)).containsExactly(userId);
        assertThat(repository.hasRoutedDiscordDestination(notice)).isTrue();
        repository.savePreference(userId, new UserNotificationPreferenceSaveDto(
                NotificationType.SYSTEM_NOTICE, minimum, browser, discord, enabled));
        assertThat(repository.findRoutedBrowserUserIds(notice).contains(userId)).isEqualTo(expectedBrowser);
        assertThat(repository.hasRoutedDiscordDestination(notice)).isEqualTo(expectedDiscord);
        UserNotificationPreferenceDetailDto saved = repository.findPreferencesByUserId(userId).getFirst();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getNotificationType()).isEqualTo(NotificationType.SYSTEM_NOTICE);
        assertThat(saved.getMinimumLevel()).isEqualTo(minimum);
        assertThat(saved.getBrowserEnabled()).isEqualTo(browser);
        assertThat(saved.getDiscordEnabled()).isEqualTo(discord);
        assertThat(saved.getEnabled()).isEqualTo(enabled);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        repository.savePreference(userId, new UserNotificationPreferenceSaveDto(
                NotificationType.SYSTEM_NOTICE, NotificationLevel.INFO, true, true, true));
        assertThat(repository.findPreferencesByUserId(userId)).hasSize(1);
        assertThat(repository.findRoutedBrowserUserIds(notice)).containsExactly(userId);
        assertThat(repository.hasRoutedDiscordDestination(notice)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "node-1", "other-node"})
    void sourceMatchingAndRouteLevelDecideWhichUsersReceiveEachChannel(String source) {
        long globalUser = user();
        long specificUser = user();
        long highLevelUser = user();
        repository.saveRouteSetting(route(globalUser, null, NotificationLevel.INFO, true, false, true));
        repository.saveRouteSetting(route(specificUser, "node-1", NotificationLevel.INFO, false, true, true));
        repository.saveRouteSetting(route(highLevelUser, null, NotificationLevel.CRITICAL, true, true, true));
        repository.saveRouteSetting(route(user(), null, NotificationLevel.INFO, true, true, false));
        BrowserNotificationCreateDto notice = notice(source, NotificationLevel.WARNING, null, null);
        assertThat(repository.findRoutedBrowserUserIds(notice)).containsExactly(globalUser);
        assertThat(repository.hasRoutedDiscordDestination(notice)).isEqualTo("node-1".equals(source));
        BrowserNotificationCreateDto missingLevel = notice(source, null, null, null);
        assertThat(repository.findRoutedBrowserUserIds(missingLevel)).isEmpty();
        assertThat(repository.hasRoutedDiscordDestination(missingLevel)).isFalse();
    }

    @Test
    void notificationRoleRoutesDeduplicateUsersAndRespectDisabledAssignments() {
        long first = user();
        long second = user();
        String roleCode = NotificationRoleCode.ITEM_STOCK.getStoredName();
        jdbc.update("delete from t_user_notification_roles where notification_role_id="
                + "(select id from m_notification_roles where role_code=?)", roleCode);
        assignRole(first, true);
        assignRole(second, false);
        repository.saveRouteSetting(new NotificationRouteSettingSaveRequest(null, NotificationType.SYSTEM_NOTICE,
                NotificationSourceType.SYSTEM, null, NotificationTargetType.NOTIFICATION_ROLE,
                roleCode, null, NotificationLevel.INFO, true, true, true));
        repository.saveRouteSetting(route(first, null, NotificationLevel.INFO, true, true, true));
        BrowserNotificationCreateDto notice = notice(null, NotificationLevel.WARNING, null, null);
        assertThat(repository.findRoutedBrowserUserIds(notice)).containsExactly(first);
        assertThat(repository.hasRoutedDiscordDestination(notice)).isTrue();
        repository.savePreference(first, new UserNotificationPreferenceSaveDto(
                NotificationType.SYSTEM_NOTICE, NotificationLevel.INFO, false, false, true));
        assertThat(repository.findRoutedBrowserUserIds(notice)).isEmpty();
        assertThat(repository.hasRoutedDiscordDestination(notice)).isFalse();
        jdbc.update("update m_notification_roles set enabled=false where role_code=?", roleCode);
        assertThat(repository.findRoutedBrowserUserIds(notice)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "{\"node\":1}"})
    void notificationContentAndMetadataRoundTripWithoutLosingFields(String metadata) {
        BrowserNotificationCreateDto input = notice("node-1", NotificationLevel.WARNING, metadata, null);
        UUID id = repository.createNotification(input);
        BrowserNotificationDetailDto saved = repository.findNotification(id).orElseThrow();
        assertThat(saved.getNotificationId()).isEqualTo(id);
        assertThat(saved.getNotificationType()).isEqualTo(NotificationType.SYSTEM_NOTICE);
        assertThat(saved.getLevel()).isEqualTo(NotificationLevel.WARNING);
        assertThat(saved.getTitle()).isEqualTo(input.getTitle());
        assertThat(saved.getMessage()).isEqualTo(input.getMessage());
        assertThat(saved.getSourceType()).isEqualTo(NotificationSourceType.SYSTEM);
        assertThat(saved.getSourceId()).isEqualTo("node-1");
        assertThat(saved.getExpiresAt()).isNull();
        assertThat(saved.getMetadataJson()).contains(metadata == null || metadata.isBlank() ? "{}" : "node");
        assertThat(repository.findNotification(UUID.randomUUID())).isEmpty();
    }

    @Test
    void deliveryReadAndDismissAreScopedToTheRecipient() {
        long recipient = user();
        long other = user();
        UUID id = repository.createNotification(notice("node-1", NotificationLevel.WARNING, null, null));
        Long userNotificationId = repository.createUserNotification(id, recipient);
        UserBrowserNotificationDetailDto unread = repository.findUnreadByUserId(recipient, 20, 0, true, true).getFirst();
        assertThat(unread.getId()).isEqualTo(userNotificationId);
        assertThat(unread.getNotificationId()).isEqualTo(id);
        assertThat(unread.getDeliveryStatus()).isEqualTo(NotificationDeliveryStatus.PENDING);
        assertThat(unread.getReadStatus()).isEqualTo(NotificationReadStatus.UNREAD);
        assertThat(repository.countUnreadByUserId(recipient, true, true)).isEqualTo(1);
        assertThat(repository.markDelivered(id, other)).isZero();
        assertThat(repository.markRead(id, other)).isZero();
        assertThat(repository.dismiss(id, other)).isZero();
        assertThat(repository.markDelivered(id, recipient)).isEqualTo(1);
        assertThat(repository.markRead(id, recipient)).isEqualTo(1);
        UserBrowserNotificationDetailDto read = repository.findByUserId(recipient, 20, 0, true, true).getFirst();
        assertThat(read.getDeliveryStatus()).isEqualTo(NotificationDeliveryStatus.DELIVERED);
        assertThat(read.getReadStatus()).isEqualTo(NotificationReadStatus.READ);
        assertThat(read.getDeliveredAt()).isNotNull();
        assertThat(read.getReadAt()).isNotNull();
        assertThat(repository.countUnreadByUserId(recipient, true, true)).isZero();
        assertThat(repository.findUnreadByUserId(recipient, 20, 0, true, true)).isEmpty();
        assertThat(repository.dismiss(id, recipient)).isEqualTo(1);
        assertThat(repository.findByUserId(recipient, 20, 0, true, true)).isEmpty();
        assertThat(repository.findByUserId(other, 20, 0, true, true)).isEmpty();
    }

    @Test
    void expiredNotificationsAreExcludedAndPaginationIsStable() {
        long recipient = user();
        UUID first = repository.createNotification(notice(null, NotificationLevel.INFO, null, null));
        UUID second = repository.createNotification(notice(null, NotificationLevel.INFO, null, null));
        UUID expired = repository.createNotification(notice(null, NotificationLevel.INFO, null,
                LocalDateTime.of(2000, 1, 1, 0, 0)));
        repository.createUserNotification(first, recipient);
        repository.createUserNotification(second, recipient);
        repository.createUserNotification(expired, recipient);
        assertThat(repository.countUnreadByUserId(recipient, true, true)).isEqualTo(2);
        assertThat(repository.findByUserId(recipient, 1, 0, true, true))
                .extracting(UserBrowserNotificationDetailDto::getNotificationId).containsExactly(second);
        assertThat(repository.findByUserId(recipient, 1, 1, true, true))
                .extracting(UserBrowserNotificationDetailDto::getNotificationId).containsExactly(first);
        assertThat(repository.findUnreadByUserId(recipient, 20, 0, true, true))
                .extracting(UserBrowserNotificationDetailDto::getNotificationId).containsExactly(second, first);
    }

    @Test
    void cursorIgnoresNewAndBackdatedNotificationsAndSurvivesRemoval() {
        long recipient = user();
        long other = user();
        for (int i = 0; i < 5; i++) {
            repository.createUserNotification(repository.createNotification(
                    notice(null, NotificationLevel.INFO, null, null)), recipient);
        }
        List<Long> expected = repository.findByUserId(recipient, 20, 0, true, true).stream()
                .map(UserBrowserNotificationDetailDto::getId).toList();
        jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.NotificationPageDto first =
                repository.findPage(recipient, 2, null, false, true, true);
        UUID newNotice = repository.createNotification(notice(null, NotificationLevel.INFO, null, null));
        repository.createUserNotification(newNotice, recipient);
        repository.createUserNotification(repository.createNotification(notice(null, NotificationLevel.INFO, null, null)), recipient);
        jdbc.update("update t_user_notifications set created_dt='2000-01-01' where user_id=? and notification_id=?",
                recipient, newNotice);

        // 取得済みの行が消えても、継続位置はoffsetのように移動しない。
        jdbc.update("delete from t_user_notifications where id=?", expected.getFirst());
        jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationCursor cursor =
                jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationCursor.decode(first.nextCursor());
        jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.NotificationPageDto second =
                repository.findPage(recipient, 20, cursor, false, true, true);
        assertThat(second.items()).extracting(UserBrowserNotificationDetailDto::getId)
                .containsExactlyElementsOf(expected.subList(2, 5));
        assertThat(second.nextCursor()).isNull();
        assertThat(repository.findPage(other, 20, cursor, false, true, true).items()).isEmpty();
        repository.markRead(second.items().getFirst().getNotificationId(), recipient);
        assertThat(repository.findPage(recipient, 20, cursor, true, true, true).items())
                .extracting(UserBrowserNotificationDetailDto::getId).containsExactlyElementsOf(expected.subList(3, 5));
    }

    private long user() {
        return jdbc.queryForObject("insert into m_users(email,password,role) "
                + "values (?,'test-only','USER') returning id",
                Long.class, UUID.randomUUID() + "@example.invalid");
    }

    private void assignRole(long userId, boolean enabled) {
        jdbc.update("insert into t_user_notification_roles(user_id,notification_role_id,enabled) "
                + "select ?,id,? from m_notification_roles where role_code=?", userId, enabled,
                NotificationRoleCode.ITEM_STOCK.getStoredName());
    }

    private NotificationRouteSettingSaveRequest route(long userId, String source,
            NotificationLevel minimum, boolean browser, boolean discord, boolean enabled) {
        return new NotificationRouteSettingSaveRequest(null, NotificationType.SYSTEM_NOTICE,
                NotificationSourceType.SYSTEM, source, NotificationTargetType.USER,
                null, userId, minimum, browser, discord, enabled);
    }

    private BrowserNotificationCreateDto notice(String source, NotificationLevel level, String metadata,
            LocalDateTime expiresAt) {
        return BrowserNotificationCreateDto.builder().notificationType(NotificationType.SYSTEM_NOTICE).level(level)
                .sourceType(NotificationSourceType.SYSTEM).sourceId(source).title("Notification test")
                .message("Test content").metadataJson(metadata).expiresAt(expiresAt).build();
    }
}
