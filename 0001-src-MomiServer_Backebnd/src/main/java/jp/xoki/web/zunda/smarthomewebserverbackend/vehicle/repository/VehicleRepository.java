package jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.repository;

import jp.xoki.web.zunda.jooq.tables.TVehicleInspectionHistories;
import jp.xoki.web.zunda.jooq.tables.TVehicles;
import jp.xoki.web.zunda.jooq.tables.records.TVehicleInspectionHistoriesRecord;
import jp.xoki.web.zunda.jooq.tables.records.TVehiclesRecord;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.dto.VehicleSaveRequest;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.model.VehicleInspectionStatus.ARCHIVED;
import java.time.Clock;

/** 車両情報と車検期限変更履歴へアクセスするRepository。 */
@Repository
@RequiredArgsConstructor
public class VehicleRepository {

	private final DSLContext dsl;

	private final Clock clock;

	/**
	 * 管理中の車両を全件取得する。
	 *
	 * @return 管理中の車両一覧
	 */
	public List<TVehiclesRecord> findAllActive() {
		return dsl.selectFrom(TVehicles.T_VEHICLES)
				.where(TVehicles.T_VEHICLES.STATUS.ne(ARCHIVED.name()))
				.fetchInto(TVehiclesRecord.class);
	}

	/**
	 * 車両IDに一致する車両を取得する。
	 *
	 * @param id 車両ID
	 * @return 車両レコード
	 */
	public Optional<TVehiclesRecord> findById(Long id) {
		return dsl.selectFrom(TVehicles.T_VEHICLES)
				.where(TVehicles.T_VEHICLES.ID.eq(id))
				.fetchOptionalInto(TVehiclesRecord.class);
	}

	/**
	 * 更新対象の車両を行ロックして取得する。
	 *
	 * @param id 車両ID
	 * @return ロック取得済み車両レコード
	 */
	public Optional<TVehiclesRecord> findByIdForUpdate(Long id) {
		return dsl.selectFrom(TVehicles.T_VEHICLES)
				.where(TVehicles.T_VEHICLES.ID.eq(id))
				.forUpdate()
				.fetchOptionalInto(TVehiclesRecord.class);
	}

	/**
	 * 管理中の車両に重複候補が存在するか確認する。
	 *
	 * @param request 登録・更新内容
	 * @param excludingId 更新時に除外する車両ID
	 * @return 重複候補が存在する場合true
	 */
	public boolean existsDuplicate(VehicleSaveRequest request, Long excludingId) {
		String registrationMonth = emptyToNull(request.getFirstRegistrationMonth());
		Condition matchingRegistrationMonth = registrationMonth == null
				? TVehicles.T_VEHICLES.FIRST_REGISTRATION_MONTH.isNull()
				: TVehicles.T_VEHICLES.FIRST_REGISTRATION_MONTH.eq(registrationMonth);
		Condition duplicate = TVehicles.T_VEHICLES.CHASSIS_NUMBER.eq(request.getChassisNumber())
				.or(TVehicles.T_VEHICLES.REGISTRATION_NUMBER.eq(request.getRegistrationNumber())
						.and(matchingRegistrationMonth));
		String identificationCode = emptyToNull(request.getVehicleIdentificationCode());
		if (identificationCode != null) {
			duplicate = duplicate.or(TVehicles.T_VEHICLES.VEHICLE_IDENTIFICATION_CODE.eq(identificationCode));
		}

		Condition condition = TVehicles.T_VEHICLES.STATUS.ne(ARCHIVED.name()).and(duplicate);
		if (excludingId != null) {
			condition = condition.and(TVehicles.T_VEHICLES.ID.ne(excludingId));
		}
		return dsl.fetchExists(dsl.selectOne().from(TVehicles.T_VEHICLES).where(condition));
	}

	/**
	 * 車両を新規登録する。
	 *
	 * @param vehicleRecord 登録する車両
	 * @return 登録された車両ID
	 */
	public Long save(TVehiclesRecord vehicleRecord) {
		return dsl.insertInto(TVehicles.T_VEHICLES)
				.set(vehicleRecord)
				.returningResult(TVehicles.T_VEHICLES.ID)
				.fetchOne(TVehicles.T_VEHICLES.ID);
	}

	/**
	 * 車両情報を更新する。
	 *
	 * @param vehicleRecord 更新する車両
	 * @return 更新件数
	 */
	public int update(TVehiclesRecord vehicleRecord) {
		return dsl.update(TVehicles.T_VEHICLES)
				.set(TVehicles.T_VEHICLES.DISPLAY_NAME, vehicleRecord.getDisplayName())
				.set(TVehicles.T_VEHICLES.REGISTRATION_NUMBER, vehicleRecord.getRegistrationNumber())
				.set(TVehicles.T_VEHICLES.CHASSIS_NUMBER, vehicleRecord.getChassisNumber())
				.set(TVehicles.T_VEHICLES.VEHICLE_IDENTIFICATION_CODE, vehicleRecord.getVehicleIdentificationCode())
				.set(TVehicles.T_VEHICLES.VEHICLE_NAME, vehicleRecord.getVehicleName())
				.set(TVehicles.T_VEHICLES.MODEL_CODE, vehicleRecord.getModelCode())
				.set(TVehicles.T_VEHICLES.FIRST_REGISTRATION_MONTH, vehicleRecord.getFirstRegistrationMonth())
				.set(TVehicles.T_VEHICLES.INSPECTION_EXPIRY_DATE, vehicleRecord.getInspectionExpiryDate())
				.set(TVehicles.T_VEHICLES.ISSUED_DATE, vehicleRecord.getIssuedDate())
				.set(TVehicles.T_VEHICLES.USAGE_CATEGORY, vehicleRecord.getUsageCategory())
				.set(TVehicles.T_VEHICLES.PURPOSE, vehicleRecord.getPurpose())
				.set(TVehicles.T_VEHICLES.STATUS, vehicleRecord.getStatus())
				.set(TVehicles.T_VEHICLES.NOTES, vehicleRecord.getNotes())
				.set(TVehicles.T_VEHICLES.UPDATED_BY, vehicleRecord.getUpdatedBy())
				.set(TVehicles.T_VEHICLES.UPDATED_DT, LocalDateTime.now(clock))
				.where(TVehicles.T_VEHICLES.ID.eq(vehicleRecord.getId()))
				.execute();
	}

	/**
	 * 車両を管理対象外へ変更する。
	 *
	 * @param id 車両ID
	 * @param updatedBy 更新者
	 * @return 更新件数
	 */
	public int archive(Long id, String updatedBy) {
		return dsl.update(TVehicles.T_VEHICLES)
				.set(TVehicles.T_VEHICLES.STATUS, ARCHIVED.name())
				.set(TVehicles.T_VEHICLES.UPDATED_BY, updatedBy)
				.set(TVehicles.T_VEHICLES.UPDATED_DT, LocalDateTime.now(clock))
				.where(TVehicles.T_VEHICLES.ID.eq(id))
				.execute();
	}

	/**
	 * 車検に関する項目だけを更新する。
	 *
	 * @param id 車両ID
	 * @param expiryDate 新しい満了日
	 * @param issuedDate 交付年月日
	 * @param status 再計算した期限状態
	 * @param updatedBy 更新者
	 * @return 更新件数
	 */
	public int updateInspection(Long id, LocalDate expiryDate, LocalDate issuedDate, String status, String updatedBy) {
		return dsl.update(TVehicles.T_VEHICLES)
				.set(TVehicles.T_VEHICLES.INSPECTION_EXPIRY_DATE, expiryDate)
				.set(TVehicles.T_VEHICLES.ISSUED_DATE, issuedDate)
				.set(TVehicles.T_VEHICLES.STATUS, status)
				.set(TVehicles.T_VEHICLES.UPDATED_BY, updatedBy)
				.set(TVehicles.T_VEHICLES.UPDATED_DT, LocalDateTime.now(clock))
				.where(TVehicles.T_VEHICLES.ID.eq(id))
				.execute();
	}

	/**
	 * 車検期限変更履歴を登録する。
	 *
	 * @param vehicleId 車両ID
	 * @param changeType 変更種別
	 * @param previousExpiryDate 変更前満了日
	 * @param newExpiryDate 変更後満了日
	 * @param changedBy 変更者
	 */
	public void saveHistory(Long vehicleId, String changeType, LocalDate previousExpiryDate,
			LocalDate newExpiryDate, String changedBy) {
		dsl.insertInto(TVehicleInspectionHistories.T_VEHICLE_INSPECTION_HISTORIES)
				.set(TVehicleInspectionHistories.T_VEHICLE_INSPECTION_HISTORIES.VEHICLE_ID, vehicleId)
				.set(TVehicleInspectionHistories.T_VEHICLE_INSPECTION_HISTORIES.CHANGE_TYPE, changeType)
				.set(TVehicleInspectionHistories.T_VEHICLE_INSPECTION_HISTORIES.PREVIOUS_EXPIRY_DATE,
						previousExpiryDate)
				.set(TVehicleInspectionHistories.T_VEHICLE_INSPECTION_HISTORIES.NEW_EXPIRY_DATE, newExpiryDate)
				.set(TVehicleInspectionHistories.T_VEHICLE_INSPECTION_HISTORIES.CHANGED_BY, changedBy)
				.set(TVehicleInspectionHistories.T_VEHICLE_INSPECTION_HISTORIES.CHANGED_DT,
						LocalDateTime.now(clock))
				.execute();
	}

	/**
	 * 車両に紐づく車検期限変更履歴を新しい順に取得する。
	 *
	 * @param vehicleId 車両ID
	 * @return 車検期限変更履歴
	 */
	public List<TVehicleInspectionHistoriesRecord> findHistories(Long vehicleId) {
		return dsl.selectFrom(TVehicleInspectionHistories.T_VEHICLE_INSPECTION_HISTORIES)
				.where(TVehicleInspectionHistories.T_VEHICLE_INSPECTION_HISTORIES.VEHICLE_ID.eq(vehicleId))
				.orderBy(TVehicleInspectionHistories.T_VEHICLE_INSPECTION_HISTORIES.CHANGED_DT.desc(),
						TVehicleInspectionHistories.T_VEHICLE_INSPECTION_HISTORIES.ID.desc())
				.fetchInto(TVehicleInspectionHistoriesRecord.class);
	}

	private String emptyToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
