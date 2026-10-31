package jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.service;

import jp.xoki.web.zunda.jooq.tables.records.TVehiclesRecord;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.dto.VehicleDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.dto.VehicleSaveRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.model.VehicleInspectionStatus;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.repository.VehicleRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.time.ApplicationClockFactory;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {
	private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);
	private static final Clock FIXED_CLOCK = Clock.fixed(
			Instant.parse("2026-10-16T15:00:00Z"), ZoneId.of("Asia/Tokyo"));

	@Mock
	private VehicleRepository vehicleRepository;

	private VehicleService vehicleService;

	@BeforeEach
	void setUp() {
		vehicleService = new VehicleService(vehicleRepository, FIXED_CLOCK, mock(org.springframework.context.ApplicationEventPublisher.class));
	}

	@Test
	void detailReturnsVehicleUsingTheSameStatusCalculation() {
		when(vehicleRepository.findById(10L)).thenReturn(Optional.of(
				vehicleRecord(10L, TODAY.plusDays(5), "VALID")));

		VehicleDetailDto result = vehicleService.detail(10L);

		assertThat(result.getId()).isEqualTo(10L);
		assertThat(result.getStatus()).isEqualTo(VehicleInspectionStatus.URGENT);
		assertThat(result.getDaysRemaining()).isEqualTo(5L);
	}

	@Test
	void detailRejectsMissingVehicle() {
		when(vehicleRepository.findById(10L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> vehicleService.detail(10L))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessageContaining("vehicle").hasMessageContaining("10");
	}

	@Test
	void listOrdersExpiredVehiclesBeforeValidVehicles() {
		TVehiclesRecord valid = vehicleRecord(1L, TODAY.plusDays(60), "VALID");
		TVehiclesRecord expired = vehicleRecord(2L, TODAY.minusDays(1), "VALID");
		when(vehicleRepository.findAllActive()).thenReturn(List.of(valid, expired));

		List<VehicleDetailDto> result = vehicleService.list();

		assertThat(result).extracting(VehicleDetailDto::getId).containsExactly(2L, 1L);
		assertThat(result.get(0).getStatus()).isEqualTo(VehicleInspectionStatus.EXPIRED);
		assertThat(result.get(1).getStatus()).isEqualTo(VehicleInspectionStatus.VALID);
	}

	@ParameterizedTest
	@MethodSource("expiryBoundaries")
	void calculatesInspectionStatusAtEveryBoundary(long daysFromToday, String storedStatus,
			VehicleInspectionStatus expected) {
		when(vehicleRepository.findAllActive()).thenReturn(List.of(
				vehicleRecord(1L, TODAY.plusDays(daysFromToday), storedStatus)));

		VehicleDetailDto result = vehicleService.list().getFirst();

		assertThat(result.getStatus()).isEqualTo(expected);
		assertThat(result.getDaysRemaining()).isEqualTo(daysFromToday);
	}

	private static Stream<Arguments> expiryBoundaries() {
		return Stream.of(
				Arguments.of(-1L, "VALID", VehicleInspectionStatus.EXPIRED),
				Arguments.of(0L, "VALID", VehicleInspectionStatus.URGENT),
				Arguments.of(7L, "VALID", VehicleInspectionStatus.URGENT),
				Arguments.of(8L, "VALID", VehicleInspectionStatus.DUE_SOON),
				Arguments.of(30L, "VALID", VehicleInspectionStatus.DUE_SOON),
				Arguments.of(31L, "VALID", VehicleInspectionStatus.VALID),
				Arguments.of(31L, "ARCHIVED", VehicleInspectionStatus.ARCHIVED));
	}

	@Test
	void createStoresEnglishStatusAndInitialHistory() {
		VehicleSaveRequest request = saveRequest(TODAY.plusDays(5));
		when(vehicleRepository.existsDuplicate(org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.isNull())).thenReturn(false);
		when(vehicleRepository.save(org.mockito.ArgumentMatchers.any())).thenReturn(10L);
		when(vehicleRepository.findById(10L)).thenReturn(Optional.of(
				vehicleRecord(10L, request.getInspectionExpiryDate(), "URGENT")));

		VehicleDetailDto result = vehicleService.create(request, "operator@example.com");

		ArgumentCaptor<TVehiclesRecord> vehicleRecord = ArgumentCaptor.forClass(TVehiclesRecord.class);
		verify(vehicleRepository).save(vehicleRecord.capture());
		assertThat(vehicleRecord.getValue().getStatus()).isEqualTo("URGENT");
		assertThat(vehicleRecord.getValue().getCreatedBy()).isEqualTo("operator@example.com");
		verify(vehicleRepository).saveHistory(10L, "CREATED", null,
				request.getInspectionExpiryDate(), "operator@example.com");
		assertThat(result.getStatus()).isEqualTo(VehicleInspectionStatus.URGENT);
	}

	@Test
	void updateWritesHistoryOnlyWhenExpiryDateChanges() {
		LocalDate previousExpiry = TODAY.plusDays(10);
		LocalDate newExpiry = TODAY.plusYears(2);
		VehicleSaveRequest request = saveRequest(newExpiry);
		TVehiclesRecord current = vehicleRecord(10L, previousExpiry, "DUE_SOON");
		TVehiclesRecord updated = vehicleRecord(10L, newExpiry, "VALID");
		when(vehicleRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(current));
		when(vehicleRepository.existsDuplicate(org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.eq(10L))).thenReturn(false);
		when(vehicleRepository.findById(10L)).thenReturn(Optional.of(updated));

		VehicleDetailDto result = vehicleService.update(10L, request, "operator@example.com");

		verify(vehicleRepository).saveHistory(10L, "EXPIRY_UPDATED", previousExpiry, newExpiry,
				"operator@example.com");
		assertThat(result.getInspectionExpiryDate()).isEqualTo(newExpiry);
		assertThat(result.getStatus()).isEqualTo(VehicleInspectionStatus.VALID);
	}

	@Test
	void archiveMarksVehicleAndWritesHistory() {
		LocalDate expiryDate = TODAY.plusDays(10);
		TVehiclesRecord current = vehicleRecord(10L, expiryDate, "DUE_SOON");
		TVehiclesRecord archived = vehicleRecord(10L, expiryDate, "ARCHIVED");
		when(vehicleRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(current));
		when(vehicleRepository.findById(10L)).thenReturn(Optional.of(archived));

		VehicleDetailDto result = vehicleService.archive(10L, "admin@example.com");

		verify(vehicleRepository).archive(10L, "admin@example.com");
		verify(vehicleRepository).saveHistory(10L, "ARCHIVED", expiryDate, expiryDate,
				"admin@example.com");
		assertThat(result.getStatus()).isEqualTo(VehicleInspectionStatus.ARCHIVED);
	}

	private VehicleSaveRequest saveRequest(LocalDate expiryDate) {
		return VehicleSaveRequest.builder()
				.displayName("自家用車")
				.registrationNumber("品川 300 あ 12-34")
				.chassisNumber("TEST-CHASSIS-001")
				.vehicleName("テスト車")
				.inspectionExpiryDate(expiryDate)
				.build();
	}

	private TVehiclesRecord vehicleRecord(Long id, LocalDate expiryDate, String status) {
		TVehiclesRecord vehicleRecord = new TVehiclesRecord();
		vehicleRecord.setId(id);
		vehicleRecord.setDisplayName("自家用車");
		vehicleRecord.setRegistrationNumber("品川 300 あ 12-34");
		vehicleRecord.setChassisNumber("TEST-CHASSIS-001");
		vehicleRecord.setVehicleName("テスト車");
		vehicleRecord.setInspectionExpiryDate(expiryDate);
		vehicleRecord.setStatus(status);
		vehicleRecord.setCreatedBy("operator@example.com");
		vehicleRecord.setUpdatedBy("operator@example.com");
		vehicleRecord.setCreatedDt(LocalDateTime.now(ApplicationClockFactory.systemClock()));
		vehicleRecord.setUpdatedDt(LocalDateTime.now(ApplicationClockFactory.systemClock()));
		return vehicleRecord;
	}
}
