package jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.service;

import jp.xoki.web.zunda.jooq.tables.records.TVehicleInspectionHistoriesRecord;
import jp.xoki.web.zunda.jooq.tables.records.TVehiclesRecord;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.ResourceNotFoundException;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.OptimisticLockConflictException;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.dto.VehicleDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.dto.VehicleInspectionHistoryDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.dto.VehicleInspectionRenewalRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.dto.VehicleSaveRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.model.VehicleInspectionStatus;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.model.VehicleSavedEvent;
import org.springframework.context.ApplicationEventPublisher;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.constants.VehicleServiceConstants.EXPIRY_UPDATED;
import static jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.constants.VehicleServiceConstants.VEHICLE_RESOURCE;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.constants.VehicleMessageConstants;

/** 車両情報、車検期限状態、変更履歴を扱うService。 */
@Service
public class VehicleService {

	private final VehicleRepository vehicleRepository;
	private final Clock clock;
	private final ApplicationEventPublisher events;

	/** 車両管理Serviceの依存を受け取る。
	 *
	 * @param vehicleRepository 車両Repository
	 * @param clock 共通の業務時刻
	 */
	public VehicleService(VehicleRepository vehicleRepository, Clock clock,
			ApplicationEventPublisher events) {
		this.vehicleRepository = vehicleRepository;
		this.clock = clock;
		this.events = events;
	}

	/**
	 * 管理中の車両を期限の危険度順に取得する。
	 *
	 * @return 車両一覧
	 */
	@Transactional(readOnly = true)
	public List<VehicleDetailDto> list() {
		return vehicleRepository.findAllActive().stream()
				.map(this::toDetailDto)
				.sorted(Comparator.comparingInt((VehicleDetailDto vehicle) -> statusOrder(vehicle.getStatus()))
						.thenComparing(VehicleDetailDto::getInspectionExpiryDate)
						.thenComparing(VehicleDetailDto::getId))
				.toList();
	}

	/**
	 * 車両IDに一致する車両詳細を取得する。
	 *
	 * @param id 車両ID
	 * @return 車両詳細
	 */
	@Transactional(readOnly = true)
	public VehicleDetailDto detail(Long id) {
		return loadDetail(id);
	}

	private VehicleDetailDto loadDetail(Long id) {
		return vehicleRepository.findById(id)
				.map(this::toDetailDto)
				.orElseThrow(() -> new ResourceNotFoundException(VEHICLE_RESOURCE, id));
	}

	/**
	 * 車両を新規登録する。
	 *
	 * @param request 登録内容
	 * @param operator 登録者
	 * @return 登録後の車両詳細
	 */
	@Transactional
	public VehicleDetailDto create(VehicleSaveRequest request, String operator) {
		VehicleSaveRequest normalized = normalize(request);
		validateDuplicate(normalized, null);
		TVehiclesRecord vehicleRecord = toRecord(normalized, operator);
		LocalDateTime now = LocalDateTime.now(clock);
		vehicleRecord.setCreatedBy(operator);
		vehicleRecord.setCreatedDt(now);
		vehicleRecord.setUpdatedDt(now);
		Long id = vehicleRepository.save(vehicleRecord);
		vehicleRepository.saveHistory(id, "CREATED", null, normalized.getInspectionExpiryDate(), operator);
		events.publishEvent(new VehicleSavedEvent(id));
		return loadDetail(id);
	}

	/**
	 * 車両情報を更新する。
	 *
	 * @param id 車両ID
	 * @param request 更新内容
	 * @param operator 更新者
	 * @return 更新後の車両詳細
	 */
	@Transactional
	public VehicleDetailDto update(Long id, VehicleSaveRequest request, String operator) {
		TVehiclesRecord current = vehicleRepository.findByIdForUpdate(id)
				.orElseThrow(() -> new ResourceNotFoundException(VEHICLE_RESOURCE, id));
		if (VehicleInspectionStatus.ARCHIVED.name().equals(current.getStatus())) {
			throw new IllegalArgumentException(VehicleMessageConstants.ARCHIVED_UPDATE_FORBIDDEN);
		}
		VehicleSaveRequest normalized = normalize(request);
		validateDuplicate(normalized, id);
		TVehiclesRecord updated = toRecord(normalized, operator);
		updated.setId(id);
		vehicleRepository.update(updated);
		if (!Objects.equals(current.getInspectionExpiryDate(), normalized.getInspectionExpiryDate())) {
			vehicleRepository.saveHistory(id, EXPIRY_UPDATED, current.getInspectionExpiryDate(),
					normalized.getInspectionExpiryDate(), operator);
			events.publishEvent(new VehicleSavedEvent(id));
		}
		return loadDetail(id);
	}

	/**
	 * 車検情報だけを更新する。
	 *
	 * 登録済み満了日を行ロック下で照合し、期限更新と履歴を同じトランザクションで保存する。
	 * @param id 車両ID
	 * @param request 車検更新内容
	 * @param operator 更新者
	 * @return 更新後の車両詳細
	 */
	@Transactional
	public VehicleDetailDto renewInspection(Long id, VehicleInspectionRenewalRequest request, String operator) {
		TVehiclesRecord current = vehicleRepository.findByIdForUpdate(id)
				.orElseThrow(() -> new ResourceNotFoundException(VEHICLE_RESOURCE, id));
		if (VehicleInspectionStatus.ARCHIVED.name().equals(current.getStatus())) {
			throw new IllegalArgumentException(VehicleMessageConstants.ARCHIVED_UPDATE_FORBIDDEN);
		}
		if (!Objects.equals(current.getInspectionExpiryDate(), request.previousExpiryDate())) {
			throw new OptimisticLockConflictException(VehicleMessageConstants.INSPECTION_EXPIRY_CONFLICT);
		}
		if (request.inspectionExpiryDate() == null || current.getInspectionExpiryDate() == null
				|| !request.inspectionExpiryDate().isAfter(current.getInspectionExpiryDate())) {
			throw new IllegalArgumentException(VehicleMessageConstants.INSPECTION_EXPIRY_NOT_EXTENDED);
		}
		LocalDate issuedDate = request.issuedDate() == null ? current.getIssuedDate() : request.issuedDate();
		vehicleRepository.updateInspection(id, request.inspectionExpiryDate(), issuedDate,
				calculateStatus(request.inspectionExpiryDate(), null).name(), operator);
		vehicleRepository.saveHistory(id, EXPIRY_UPDATED, current.getInspectionExpiryDate(),
				request.inspectionExpiryDate(), operator);
		events.publishEvent(new VehicleSavedEvent(id));
		return loadDetail(id);
	}

	/**
	 * 車両を管理対象外へ変更する。
	 *
	 * @param id 車両ID
	 * @param operator 更新者
	 * @return 管理終了後の車両詳細
	 */
	@Transactional
	public VehicleDetailDto archive(Long id, String operator) {
		TVehiclesRecord current = vehicleRepository.findByIdForUpdate(id)
				.orElseThrow(() -> new ResourceNotFoundException(VEHICLE_RESOURCE, id));
		if (!VehicleInspectionStatus.ARCHIVED.name().equals(current.getStatus())) {
			vehicleRepository.archive(id, operator);
			vehicleRepository.saveHistory(id, "ARCHIVED", current.getInspectionExpiryDate(),
					current.getInspectionExpiryDate(), operator);
		}
		return loadDetail(id);
	}

	/**
	 * 車両の車検期限変更履歴を取得する。
	 *
	 * @param id 車両ID
	 * @return 車検期限変更履歴
	 */
	@Transactional(readOnly = true)
	public List<VehicleInspectionHistoryDto> history(Long id) {
		if (vehicleRepository.findById(id).isEmpty()) {
			throw new ResourceNotFoundException(VEHICLE_RESOURCE, id);
		}
		return vehicleRepository.findHistories(id).stream()
				.map(this::toHistoryDto)
				.toList();
	}

	private void validateDuplicate(VehicleSaveRequest request, Long excludingId) {
		if (vehicleRepository.existsDuplicate(request, excludingId)) {
			throw new IllegalArgumentException(VehicleMessageConstants.DUPLICATE_CANDIDATE);
		}
	}

	private VehicleSaveRequest normalize(VehicleSaveRequest request) {
		return VehicleSaveRequest.builder()
				.displayName(request.getDisplayName().trim())
				.registrationNumber(request.getRegistrationNumber().trim())
				.chassisNumber(request.getChassisNumber().trim())
				.vehicleIdentificationCode(emptyToNull(request.getVehicleIdentificationCode()))
				.vehicleName(request.getVehicleName().trim())
				.modelCode(emptyToNull(request.getModelCode()))
				.firstRegistrationMonth(emptyToNull(request.getFirstRegistrationMonth()))
				.inspectionExpiryDate(request.getInspectionExpiryDate())
				.issuedDate(request.getIssuedDate())
				.usageCategory(emptyToNull(request.getUsageCategory()))
				.purpose(emptyToNull(request.getPurpose()))
				.notes(emptyToNull(request.getNotes()))
				.build();
	}

	private TVehiclesRecord toRecord(VehicleSaveRequest request, String operator) {
		TVehiclesRecord vehicleRecord = new TVehiclesRecord();
		vehicleRecord.setDisplayName(request.getDisplayName().trim());
		vehicleRecord.setRegistrationNumber(request.getRegistrationNumber().trim());
		vehicleRecord.setChassisNumber(request.getChassisNumber().trim());
		vehicleRecord.setVehicleIdentificationCode(emptyToNull(request.getVehicleIdentificationCode()));
		vehicleRecord.setVehicleName(request.getVehicleName().trim());
		vehicleRecord.setModelCode(emptyToNull(request.getModelCode()));
		vehicleRecord.setFirstRegistrationMonth(emptyToNull(request.getFirstRegistrationMonth()));
		vehicleRecord.setInspectionExpiryDate(request.getInspectionExpiryDate());
		vehicleRecord.setIssuedDate(request.getIssuedDate());
		vehicleRecord.setUsageCategory(emptyToNull(request.getUsageCategory()));
		vehicleRecord.setPurpose(emptyToNull(request.getPurpose()));
		vehicleRecord.setStatus(calculateStatus(request.getInspectionExpiryDate(), null).name());
		vehicleRecord.setNotes(emptyToNull(request.getNotes()));
		vehicleRecord.setUpdatedBy(operator);
		return vehicleRecord;
	}

	private VehicleDetailDto toDetailDto(TVehiclesRecord vehicleRecord) {
		VehicleInspectionStatus status = calculateStatus(vehicleRecord.getInspectionExpiryDate(), vehicleRecord.getStatus());
		Long daysRemaining = vehicleRecord.getInspectionExpiryDate() == null ? null
				: ChronoUnit.DAYS.between(today(), vehicleRecord.getInspectionExpiryDate());
		return VehicleDetailDto.builder()
				.id(vehicleRecord.getId())
				.displayName(vehicleRecord.getDisplayName())
				.registrationNumber(vehicleRecord.getRegistrationNumber())
				.chassisNumber(vehicleRecord.getChassisNumber())
				.vehicleIdentificationCode(vehicleRecord.getVehicleIdentificationCode())
				.vehicleName(vehicleRecord.getVehicleName())
				.modelCode(vehicleRecord.getModelCode())
				.firstRegistrationMonth(vehicleRecord.getFirstRegistrationMonth())
				.inspectionExpiryDate(vehicleRecord.getInspectionExpiryDate())
				.issuedDate(vehicleRecord.getIssuedDate())
				.usageCategory(vehicleRecord.getUsageCategory())
				.purpose(vehicleRecord.getPurpose())
				.status(status)
				.daysRemaining(daysRemaining)
				.notes(vehicleRecord.getNotes())
				.createdBy(vehicleRecord.getCreatedBy())
				.updatedBy(vehicleRecord.getUpdatedBy())
				.createdAt(vehicleRecord.getCreatedDt())
				.updatedAt(vehicleRecord.getUpdatedDt())
				.build();
	}

	private VehicleInspectionHistoryDto toHistoryDto(TVehicleInspectionHistoriesRecord vehicleRecord) {
		return VehicleInspectionHistoryDto.builder()
				.id(vehicleRecord.getId())
				.vehicleId(vehicleRecord.getVehicleId())
				.changeType(vehicleRecord.getChangeType())
				.previousExpiryDate(vehicleRecord.getPreviousExpiryDate())
				.newExpiryDate(vehicleRecord.getNewExpiryDate())
				.changedBy(vehicleRecord.getChangedBy())
				.changedAt(vehicleRecord.getChangedDt())
				.build();
	}

	private VehicleInspectionStatus calculateStatus(LocalDate expiryDate, String storedStatus) {
		if (VehicleInspectionStatus.ARCHIVED.name().equals(storedStatus)) {
			return VehicleInspectionStatus.ARCHIVED;
		}
		if (expiryDate == null) {
			return VehicleInspectionStatus.UNKNOWN;
		}
		long days = ChronoUnit.DAYS.between(today(), expiryDate);
		if (days < 0) {
			return VehicleInspectionStatus.EXPIRED;
		}
		if (days <= 7) {
			return VehicleInspectionStatus.URGENT;
		}
		if (days <= 30) {
			return VehicleInspectionStatus.DUE_SOON;
		}
		return VehicleInspectionStatus.VALID;
	}

	private int statusOrder(VehicleInspectionStatus status) {
		return switch (status) {
			case EXPIRED -> 0;
			case URGENT -> 1;
			case DUE_SOON -> 2;
			case UNKNOWN -> 3;
			case VALID -> 4;
			case ARCHIVED -> 5;
		};
	}

	private String emptyToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private LocalDate today() {
		return LocalDate.now(clock);
	}
}
