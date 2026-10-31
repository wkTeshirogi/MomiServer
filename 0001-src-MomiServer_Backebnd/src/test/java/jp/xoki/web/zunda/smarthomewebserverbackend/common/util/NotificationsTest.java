package jp.xoki.web.zunda.smarthomewebserverbackend.common.util;

import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.BrowserNotificationCreateDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.NotificationGlobalSettingDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationLevel;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationSourceType;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationType;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.repository.NotificationRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.service.NotificationGlobalSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.time.ApplicationClockFactory;

@ExtendWith(MockitoExtension.class)
class NotificationsTest {

	@org.mockito.Spy
	private java.time.Clock clock = ApplicationClockFactory.systemClock();

	@Mock
	private DiscordNotice discordNotice;

	@Mock
	private NotificationRepository notificationRepository;

	@Mock
	private NotificationGlobalSettingService notificationGlobalSettingService;

	@BeforeEach
	void setUp() {
		when(notificationGlobalSettingService.detail()).thenReturn(NotificationGlobalSettingDetailDto.builder()
				.enabled(true).browserEnabled(true).discordEnabled(true)
				.discordMinimumLevel(NotificationLevel.WARNING).retentionDays(30).build());
	}

	@InjectMocks
	private Notifications notifications;

	@Test
	void sendNoticeRegistersBrowserNotificationWithoutDiscordWhenInfo() {
		UUID notificationId = UUID.randomUUID();
		BrowserNotificationCreateDto request = request(NotificationLevel.INFO);
		when(notificationRepository.createNotification(any())).thenReturn(notificationId);

		UUID result = notifications.sendNotice(request, List.of(1L, 2L));

		assertThat(result).isEqualTo(notificationId);
		verifyRetainedRequest(request);
		verify(notificationRepository).createUserNotification(notificationId, 1L);
		verify(notificationRepository).createUserNotification(notificationId, 2L);
		verify(discordNotice, never()).sendNotice(
				request.getTitle(),
				request.getMessage(),
				request.getLevel());
	}

	@Test
	void sendNoticeRegistersBrowserNotificationAndDiscordWhenWarning() {
		UUID notificationId = UUID.randomUUID();
		BrowserNotificationCreateDto request = request(NotificationLevel.WARNING);
		when(notificationRepository.createNotification(any())).thenReturn(notificationId);

		UUID result = notifications.sendNotice(request, List.of(1L));

		assertThat(result).isEqualTo(notificationId);
		verifyRetainedRequest(request);
		verify(notificationRepository).createUserNotification(notificationId, 1L);
		verify(discordNotice).sendNotice(
				request.getTitle(),
				request.getMessage(),
				request.getLevel());
	}

	@Test
	void sendNoticeUsesRoutedDestinationsWhenUserIdsAreNotSpecified() {
		UUID notificationId = UUID.randomUUID();
		BrowserNotificationCreateDto request = request(NotificationLevel.WARNING);
		when(notificationRepository.findRoutedBrowserUserIds(any())).thenReturn(List.of(1L, 2L));
		when(notificationRepository.createNotification(any())).thenReturn(notificationId);
		when(notificationRepository.hasRoutedDiscordDestination(any())).thenReturn(true);

		UUID result = notifications.sendNotice(request);

		assertThat(result).isEqualTo(notificationId);
		BrowserNotificationCreateDto retained = verifyRetainedRequest(request);
		verify(notificationRepository).findRoutedBrowserUserIds(retained);
		verify(notificationRepository).hasRoutedDiscordDestination(retained);
		verify(notificationRepository).createUserNotification(notificationId, 1L);
		verify(notificationRepository).createUserNotification(notificationId, 2L);
		verify(discordNotice).sendNotice(
				request.getTitle(),
				request.getMessage(),
				request.getLevel());
	}

	@Test
	void disabledDeliveryStillStoresNotificationWithoutSending() {
		when(notificationGlobalSettingService.detail()).thenReturn(NotificationGlobalSettingDetailDto.builder()
				.enabled(false).browserEnabled(true).discordEnabled(true)
				.discordMinimumLevel(NotificationLevel.WARNING).retentionDays(30).build());
		BrowserNotificationCreateDto request = request(NotificationLevel.WARNING);
		notifications.sendNotice(request, List.of(1L));
		verifyRetainedRequest(request);
		verify(notificationRepository, never()).createUserNotification(any(), anyLong());
		verifyNoInteractions(discordNotice);
	}

	@Test
	void explicitExpiryIsPreserved() {
		BrowserNotificationCreateDto request = BrowserNotificationCreateDto.builder().level(NotificationLevel.INFO)
				.expiresAt(LocalDateTime.of(2026, 12, 31, 0, 0)).build();
		notifications.sendNotice(request, List.of());
		verify(notificationRepository).createNotification(request);
		verifyNoInteractions(discordNotice);
	}

	private BrowserNotificationCreateDto verifyRetainedRequest(BrowserNotificationCreateDto original) {
		ArgumentCaptor<BrowserNotificationCreateDto> captor = ArgumentCaptor.forClass(BrowserNotificationCreateDto.class);
		verify(notificationRepository).createNotification(captor.capture());
		BrowserNotificationCreateDto retained = captor.getValue();
		assertThat(retained).usingRecursiveComparison().ignoringFields("expiresAt").isEqualTo(original);
		assertThat(retained.getExpiresAt()).isBetween(LocalDateTime.now(ApplicationClockFactory.systemClock()).plusDays(30).minusMinutes(1),
				LocalDateTime.now(ApplicationClockFactory.systemClock()).plusDays(30));
		assertThat(original.getExpiresAt()).isNull();
		return retained;
	}

	private BrowserNotificationCreateDto request(NotificationLevel level) {
		return BrowserNotificationCreateDto.builder()
				.notificationType(NotificationType.SECURITY_ALERT)
				.level(level)
				.title("Security Event")
				.message("rate_limit_exceeded")
				.sourceType(NotificationSourceType.SECURITY_EVENT)
				.sourceId("event-1")
				.linkPath("/security/events/event-1")
				.build();
	}
}
