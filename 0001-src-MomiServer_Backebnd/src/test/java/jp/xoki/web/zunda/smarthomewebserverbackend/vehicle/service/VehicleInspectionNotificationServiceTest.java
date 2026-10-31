package jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.service;

import jp.xoki.web.zunda.jooq.tables.records.TVehiclesRecord;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.BrowserNotificationCreateDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.NotificationGlobalSettingDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationLevel;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationSourceType;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationType;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.service.NotificationGlobalSettingService;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.util.Notifications;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.model.VehicleInspectionNotificationStage;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.repository.VehicleInspectionNotificationRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleInspectionNotificationServiceTest {
	private static final LocalDate EXPIRY = LocalDate.of(2026, 11, 5);
	@Mock VehicleRepository vehicles;
	@Mock VehicleInspectionNotificationRepository history;
	@Mock Notifications notifications;
	@Mock NotificationGlobalSettingService settings;
	private VehicleInspectionNotificationService service;

	@BeforeEach
	void setUp() {
		service = new VehicleInspectionNotificationService(vehicles, history, notifications, settings);
	}

	@ParameterizedTest
	@CsvSource({
		"2026-09-04, NONE", "2026-09-05, TWO_MONTHS", "2026-10-04, TWO_MONTHS",
		"2026-10-05, ONE_MONTH", "2026-10-28, ONE_MONTH", "2026-10-29, SEVEN_DAYS",
		"2026-11-04, SEVEN_DAYS", "2026-11-05, DUE_TODAY", "2026-11-06, EXPIRED"
	})
	void selectsLatestStageAtEachBoundary(String date, String expected) {
		VehicleInspectionNotificationStage stage = VehicleInspectionNotificationStage.resolve(EXPIRY, LocalDate.parse(date));
		assertThat(stage == null ? "NONE" : stage.name()).isEqualTo(expected);
	}

	@Test
	void usesCalendarMonthsAtMonthEndAndLeapYear() {
		assertThat(VehicleInspectionNotificationStage.resolve(LocalDate.of(2026, 4, 30), LocalDate.of(2026, 2, 28)))
				.isEqualTo(VehicleInspectionNotificationStage.TWO_MONTHS);
		assertThat(VehicleInspectionNotificationStage.resolve(LocalDate.of(2024, 3, 31), LocalDate.of(2024, 2, 28)))
				.isEqualTo(VehicleInspectionNotificationStage.TWO_MONTHS);
		assertThat(VehicleInspectionNotificationStage.resolve(LocalDate.of(2024, 3, 31), LocalDate.of(2024, 2, 29)))
				.isEqualTo(VehicleInspectionNotificationStage.ONE_MONTH);
	}

	@Test
	void registersRoutedNotificationWithVehicleLinkAndPersistsIdentity() {
		enable();
		when(vehicles.findByIdForUpdate(1L)).thenReturn(Optional.of(vehicle("VALID")));
		when(history.reserve(1L, EXPIRY, VehicleInspectionNotificationStage.ONE_MONTH)).thenReturn(true);
		UUID notice = UUID.randomUUID();
		when(notifications.sendNotice(any(BrowserNotificationCreateDto.class))).thenReturn(notice);
		assertThat(service.notifyIfDue(1L, LocalDate.of(2026, 10, 10))).isTrue();
		ArgumentCaptor<BrowserNotificationCreateDto> request = ArgumentCaptor.forClass(BrowserNotificationCreateDto.class);
		verify(notifications).sendNotice(request.capture());
		assertThat(request.getValue().getNotificationType()).isEqualTo(NotificationType.VEHICLE_INSPECTION_EXPIRY);
		assertThat(request.getValue().getSourceType()).isEqualTo(NotificationSourceType.VEHICLE);
		assertThat(request.getValue().getLevel()).isEqualTo(NotificationLevel.WARNING);
		assertThat(request.getValue().getLinkPath()).isEqualTo("/vehicles/1");
		assertThat(request.getValue().getTitle()).isEqualTo("車検期限のお知らせ（1か月前）");
		assertThat(request.getValue().getMessage()).isEqualTo(
				"テスト車両（テスト 500 あ 1234）の車検有効期間満了日は2026-11-05です。満了日まであと26日です。");
		verify(history).complete(1L, EXPIRY, VehicleInspectionNotificationStage.ONE_MONTH, notice);
	}

	@Test
	void duplicateStageDoesNotCreateAnotherNotification() {
		enable();
		when(vehicles.findByIdForUpdate(1L)).thenReturn(Optional.of(vehicle("VALID")));
		when(history.reserve(1L, EXPIRY, VehicleInspectionNotificationStage.ONE_MONTH)).thenReturn(false);
		assertThat(service.notifyIfDue(1L, LocalDate.of(2026, 10, 10))).isFalse();
		verifyNoInteractions(notifications);
	}

	@Test
	void archivedVehicleIsNotNotified() {
		enable();
		when(vehicles.findByIdForUpdate(1L)).thenReturn(Optional.of(vehicle("ARCHIVED")));
		assertThat(service.notifyIfDue(1L, LocalDate.of(2026, 10, 10))).isFalse();
		verifyNoInteractions(history, notifications);
	}

	@Test
	void globallyDisabledDeliveryDoesNotConsumeNotificationStage() {
		when(settings.detail()).thenReturn(NotificationGlobalSettingDetailDto.builder().enabled(false).build());
		assertThat(service.notifyIfDue(1L, LocalDate.of(2026, 10, 10))).isFalse();
		verifyNoInteractions(vehicles, history, notifications);
	}

	@Test
	void creationFailurePropagatesToRollBackReservation() {
		enable();
		when(vehicles.findByIdForUpdate(1L)).thenReturn(Optional.of(vehicle("VALID")));
		when(history.reserve(1L, EXPIRY, VehicleInspectionNotificationStage.EXPIRED)).thenReturn(true);
		when(notifications.sendNotice(any(BrowserNotificationCreateDto.class))).thenThrow(new IllegalStateException("failed"));
		LocalDate notificationDate = EXPIRY.plusDays(1);
		assertThatThrownBy(() -> service.notifyIfDue(1L, notificationDate)).isInstanceOf(IllegalStateException.class);
		verify(history, never()).complete(any(), any(), any(), any());
	}

	@Test
	void expiredNotificationIsCriticalAndContainsOverdueDays() {
		enable();
		when(vehicles.findByIdForUpdate(1L)).thenReturn(Optional.of(vehicle("VALID")));
		when(history.reserve(1L, EXPIRY, VehicleInspectionNotificationStage.EXPIRED)).thenReturn(true);
		when(notifications.sendNotice(any(BrowserNotificationCreateDto.class))).thenReturn(UUID.randomUUID());
		service.notifyIfDue(1L, EXPIRY.plusDays(2));
		ArgumentCaptor<BrowserNotificationCreateDto> request = ArgumentCaptor.forClass(BrowserNotificationCreateDto.class);
		verify(notifications).sendNotice(request.capture());
		assertThat(request.getValue().getLevel()).isEqualTo(NotificationLevel.CRITICAL);
		assertThat(request.getValue().getTitle()).isEqualTo("車検期限のお知らせ（期限切れ）");
		assertThat(request.getValue().getMessage()).isEqualTo(
				"テスト車両（テスト 500 あ 1234）の車検有効期間満了日は2026-11-05です。満了日を2日過ぎています。");
	}

	private void enable() {
		when(settings.detail()).thenReturn(NotificationGlobalSettingDetailDto.builder().enabled(true).browserEnabled(true).build());
	}

	private TVehiclesRecord vehicle(String status) {
		TVehiclesRecord vehicle = new TVehiclesRecord();
		vehicle.setId(1L);
		vehicle.setDisplayName("テスト車両");
		vehicle.setRegistrationNumber("テスト 500 あ 1234");
		vehicle.setInspectionExpiryDate(EXPIRY);
		vehicle.setStatus(status);
		return vehicle;
	}
}
