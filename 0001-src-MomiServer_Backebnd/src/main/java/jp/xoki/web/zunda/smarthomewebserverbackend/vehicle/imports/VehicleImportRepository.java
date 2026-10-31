package jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.imports;

import jp.xoki.web.zunda.jooq.tables.TVehicleImports;
import jp.xoki.web.zunda.jooq.tables.records.TVehicleImportsRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.time.Clock;

/** t_vehicle_importsの候補値と状態だけを読み書きするRepository。 */
@Repository
public class VehicleImportRepository {

	private static final TVehicleImports TABLE = TVehicleImports.T_VEHICLE_IMPORTS;
	private final DSLContext dsl;

	private final Clock clock;

	public VehicleImportRepository(DSLContext dsl, Clock clock) {
		this.dsl = dsl;
		this.clock = clock;
	}

	public void insert(VehicleImportRow row) {
		dsl.insertInto(TABLE)
				.set(TABLE.ID, row.id())
				.set(TABLE.DISPLAY_FILENAME, row.displayFilename())
				.set(TABLE.DETECTED_MEDIA_TYPE, row.mediaType())
				.set(TABLE.STORAGE_REFERENCE, row.storageReference())
				.set(TABLE.STATUS, row.status().name())
				.set(TABLE.DISPLAY_NAME, row.candidate().display_name())
				.set(TABLE.REGISTRATION_NUMBER, row.candidate().registration_number())
				.set(TABLE.CHASSIS_NUMBER, row.candidate().chassis_number())
				.set(TABLE.VEHICLE_NAME, row.candidate().vehicle_name())
				.set(TABLE.MODEL_CODE, row.candidate().model_code())
				.set(TABLE.FIRST_REGISTRATION_MONTH, row.candidate().first_registration_month())
				.set(TABLE.INSPECTION_EXPIRY_DATE, row.candidate().inspection_expiry_date())
				.set(TABLE.ISSUED_DATE, row.candidate().issued_date())
				.set(TABLE.EXTRACTION_METHOD, row.extractionMethod())
				.set(TABLE.WARNINGS, String.join("\n", row.warnings()))
				.set(TABLE.EXPIRES_AT, row.expiresAt())
				.set(TABLE.CREATED_BY, row.createdBy())
				.set(TABLE.CREATED_DT, LocalDateTime.now(clock))
				.execute();
	}

	public VehicleImportRow find(UUID id, boolean lock) {
		TVehicleImportsRecord importRecord;
		if (lock) {
			importRecord = dsl.selectFrom(TABLE).where(TABLE.ID.eq(id)).forUpdate().fetchOne();
		} else {
			importRecord = dsl.selectFrom(TABLE).where(TABLE.ID.eq(id)).fetchOne();
		}
		return importRecord == null ? null : row(importRecord);
	}

	public int finishPending(UUID id, VehicleImportStatus status, Long vehicleId) {
		return dsl.update(TABLE)
				.set(TABLE.STATUS, status.name())
				.set(TABLE.CONFIRMED_VEHICLE_ID, vehicleId)
				.where(TABLE.ID.eq(id).and(TABLE.STATUS.eq(VehicleImportStatus.PENDING_REVIEW.name())))
				.execute();
	}

	public List<VehicleImportRow> findExpiredPending(LocalDateTime now) {
		return dsl.selectFrom(TABLE)
				.where(TABLE.STATUS.eq(VehicleImportStatus.PENDING_REVIEW.name()).and(TABLE.EXPIRES_AT.le(now)))
				.forUpdate()
				.skipLocked()
				.fetch(this::row);
	}

	public List<VehicleImportRow> findTerminalWithSource() {
		return dsl.selectFrom(TABLE)
				.where(TABLE.STATUS.ne(VehicleImportStatus.PENDING_REVIEW.name()))
				.fetch(this::row);
	}

	public Set<String> findAllStorageReferences() {
		return dsl.select(TABLE.STORAGE_REFERENCE).from(TABLE).fetchSet(TABLE.STORAGE_REFERENCE);
	}

	private VehicleImportRow row(TVehicleImportsRecord importRecord) {
		String warningText = importRecord.getWarnings();
		List<String> warnings = warningText == null || warningText.isBlank()
				? List.of()
				: Arrays.asList(warningText.split("\\n"));
		return new VehicleImportRow(importRecord.getId(), importRecord.getDisplayFilename(), importRecord.getDetectedMediaType(),
				importRecord.getStorageReference(), VehicleImportStatus.valueOf(importRecord.getStatus()),
				VehicleImportCandidate.builder()
						.display_name(importRecord.getDisplayName())
						.registration_number(importRecord.getRegistrationNumber())
						.chassis_number(importRecord.getChassisNumber())
						.vehicle_name(importRecord.getVehicleName())
						.model_code(importRecord.getModelCode())
						.first_registration_month(importRecord.getFirstRegistrationMonth())
						.inspection_expiry_date(importRecord.getInspectionExpiryDate())
						.issued_date(importRecord.getIssuedDate())
						.build(),
				importRecord.getExtractionMethod(), warnings, importRecord.getExpiresAt(), importRecord.getCreatedBy(),
				importRecord.getConfirmedVehicleId());
	}
}
