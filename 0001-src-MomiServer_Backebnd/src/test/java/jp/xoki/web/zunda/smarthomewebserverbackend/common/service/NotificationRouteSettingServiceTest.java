package jp.xoki.web.zunda.smarthomewebserverbackend.common.service;

import jp.xoki.web.zunda.smarthomewebserverbackend.auth.repository.UserRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.NotificationRouteSettingDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.NotificationRouteSettingSaveRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationLevel;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationSourceType;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationTargetType;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationType;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationRouteSettingServiceTest {

	@Mock
	private NotificationRepository notificationRepository;

	@Mock
	private UserRepository userRepository;

	@InjectMocks
	private NotificationRouteSettingService service;

	@Test
	void saveNormalizesAndSavesExistingNotificationRole() {
		NotificationRouteSettingSaveRequest request = request("item_stock");
		NotificationRouteSettingDetailDto detail = NotificationRouteSettingDetailDto.builder()
				.id(1L)
				.targetRole("ROLE_ITEM_STOCK")
				.build();
		when(notificationRepository.existsEnabledNotificationRole("ROLE_ITEM_STOCK")).thenReturn(true);
		when(notificationRepository.saveRouteSetting(org.mockito.ArgumentMatchers.any())).thenReturn(1L);
		when(notificationRepository.findRouteSettingById(1L)).thenReturn(Optional.of(detail));

		NotificationRouteSettingDetailDto result = service.save(request);

		ArgumentCaptor<NotificationRouteSettingSaveRequest> savedRequest =
				ArgumentCaptor.forClass(NotificationRouteSettingSaveRequest.class);
		verify(notificationRepository).saveRouteSetting(savedRequest.capture());
		assertThat(savedRequest.getValue().target_role()).isEqualTo("ROLE_ITEM_STOCK");
		assertThat(result).isSameAs(detail);
	}

	@Test
	void saveRejectsUnknownNotificationRoleBeforeInsert() {
		NotificationRouteSettingSaveRequest request = request("ADMIN");

		assertThatThrownBy(() -> service.save(request))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("指定された通知ロールは利用できません。");

		verify(notificationRepository, never()).saveRouteSetting(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void saveRejectsNotificationRoleMissingFromMasterBeforeInsert() {
		NotificationRouteSettingSaveRequest request = request("ROLE_ALL");
		when(notificationRepository.existsEnabledNotificationRole("ROLE_ALL")).thenReturn(false);

		assertThatThrownBy(() -> service.save(request))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("指定された通知ロールは登録されていないか、無効です。");

		verify(notificationRepository, never()).saveRouteSetting(org.mockito.ArgumentMatchers.any());
	}

	private NotificationRouteSettingSaveRequest request(String targetRole) {
		return new NotificationRouteSettingSaveRequest(
				null,
				NotificationType.INVENTORY_LOW,
				NotificationSourceType.INVENTORY,
				null,
				NotificationTargetType.NOTIFICATION_ROLE,
				targetRole,
				null,
				NotificationLevel.WARNING,
				true,
				false,
				true);
	}
}
