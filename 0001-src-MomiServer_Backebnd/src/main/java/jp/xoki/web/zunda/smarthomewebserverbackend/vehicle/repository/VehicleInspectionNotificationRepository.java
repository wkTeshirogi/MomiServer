package jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.repository;

import jp.xoki.web.zunda.jooq.tables.TVehicleInspectionNotifications;

import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.model.VehicleInspectionNotificationStage;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;


/** 通知本体の保持期間と独立して車検期限通知の重複を防止するRepository。 */
@Repository
@RequiredArgsConstructor
public class VehicleInspectionNotificationRepository {
	private static final TVehicleInspectionNotifications TABLE = TVehicleInspectionNotifications.T_VEHICLE_INSPECTION_NOTIFICATIONS;
	private static final Field<Long> VEHICLE_ID = TABLE.VEHICLE_ID;
	private static final Field<LocalDate> EXPIRY = TABLE.INSPECTION_EXPIRY_DATE;
	private static final Field<String> STAGE = TABLE.NOTIFICATION_STAGE;
	private static final Field<UUID> NOTIFICATION_ID = TABLE.NOTIFICATION_ID;
	private final DSLContext dsl;

	/** 通知枠の確保
	 *
	 * 複数プロセスでも同一車両・期限・段階の通知は一度だけ確保する。
	 *
	 * @param vehicleId 車両ID
	 * @param expiry 満了日
	 * @param stage 通知段階
	 * @return 新規確保した場合true
	 */
	public boolean reserve(Long vehicleId, LocalDate expiry, VehicleInspectionNotificationStage stage) {
		return dsl.insertInto(TABLE).set(VEHICLE_ID, vehicleId).set(EXPIRY, expiry).set(STAGE, stage.name())
				.onConflict(VEHICLE_ID, EXPIRY, STAGE).doNothing().execute() == 1;
	}

	/** 通知IDの関連付け
	 *
	 * 通知登録と同一トランザクションで、通知済み記録へ作成済みIDを設定する。
	 *
	 * @param vehicleId 車両ID
	 * @param expiry 満了日
	 * @param stage 通知段階
	 * @param notificationId 作成された通知ID
	 */
	public void complete(Long vehicleId, LocalDate expiry, VehicleInspectionNotificationStage stage, UUID notificationId) {
		dsl.update(TABLE).set(NOTIFICATION_ID, notificationId)
				.where(VEHICLE_ID.eq(vehicleId)).and(EXPIRY.eq(expiry)).and(STAGE.eq(stage.name())).execute();
	}
}
