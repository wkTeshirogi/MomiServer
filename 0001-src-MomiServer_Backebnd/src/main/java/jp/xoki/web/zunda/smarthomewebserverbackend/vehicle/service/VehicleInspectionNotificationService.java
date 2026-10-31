package jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.service;

import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.BrowserNotificationCreateDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationLevel;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationSourceType;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationType;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.service.NotificationGlobalSettingService;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.util.Notifications;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.model.VehicleInspectionNotificationStage;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.repository.VehicleInspectionNotificationRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import jp.xoki.web.zunda.jooq.tables.records.TVehiclesRecord;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.NotificationGlobalSettingDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.constants.VehicleMessageConstants;

/** 車検期限通知の登録と通知済み記録を車両単位のトランザクションで管理する。 */
@Service
@RequiredArgsConstructor
public class VehicleInspectionNotificationService {
	private final VehicleRepository vehicles;
	private final VehicleInspectionNotificationRepository history;
	private final Notifications notifications;
	private final NotificationGlobalSettingService globalSettings;

	/** 車検期限通知の作成
	 *
	 * 車両を行ロックして期限・管理状態を再確認し、未通知段階だけを既存通知基盤へ登録する。
	 *
	 * @param vehicleId 車両ID
	 * @param today 日本時間の判定日
	 * @return 通知を作成した場合true
	 */
	@Transactional
	public boolean notifyIfDue(Long vehicleId, LocalDate today) {
		NotificationGlobalSettingDetailDto settings = globalSettings.detail();
		if (!Boolean.TRUE.equals(settings.getEnabled()) || (!Boolean.TRUE.equals(settings.getBrowserEnabled())
				&& !Boolean.TRUE.equals(settings.getDiscordEnabled()))) return false;

		// 日次判定と保存直後の判定が並行しても、期限更新前の情報で通知を確定しないよう車両を再取得する。
		TVehiclesRecord vehicle = vehicles.findByIdForUpdate(vehicleId).orElse(null);
		if (vehicle == null || "ARCHIVED".equals(vehicle.getStatus())) return false;
		LocalDate expiry = vehicle.getInspectionExpiryDate();
		VehicleInspectionNotificationStage stage = VehicleInspectionNotificationStage.resolve(expiry, today);

		// 車両・満了日・通知段階の組で確保するため、毎日の重複は抑止し、車検更新後の次周期は通知できる。
		if (stage == null || !history.reserve(vehicleId, expiry, stage)) return false;
		long days = ChronoUnit.DAYS.between(today, expiry);
		NotificationLevel level = stage == VehicleInspectionNotificationStage.EXPIRED
				|| stage == VehicleInspectionNotificationStage.DUE_TODAY || stage == VehicleInspectionNotificationStage.SEVEN_DAYS
				? NotificationLevel.CRITICAL : NotificationLevel.WARNING;
		String remaining = days < 0 ? VehicleMessageConstants.INSPECTION_EXPIRED_REMAINING.formatted(-days) : VehicleMessageConstants.INSPECTION_REMAINING.formatted(days);
		UUID notificationId = notifications.sendNotice(BrowserNotificationCreateDto.builder()
				.notificationType(NotificationType.VEHICLE_INSPECTION_EXPIRY).sourceType(NotificationSourceType.VEHICLE)
				.sourceId(vehicleId.toString()).level(level)
				.title(VehicleMessageConstants.INSPECTION_NOTIFICATION_TITLE.formatted(stage.getLabel()))
				.message(VehicleMessageConstants.INSPECTION_NOTIFICATION_MESSAGE.formatted(
						vehicle.getDisplayName(), vehicle.getRegistrationNumber(), expiry, remaining))
				.linkPath("/vehicles/" + vehicleId).build());

		// 通知DBレコードと通知済み記録を同じトランザクションで結び付け、片方だけ確定した状態を避ける。
		history.complete(vehicleId, expiry, stage, notificationId);
		return true;
	}
}
