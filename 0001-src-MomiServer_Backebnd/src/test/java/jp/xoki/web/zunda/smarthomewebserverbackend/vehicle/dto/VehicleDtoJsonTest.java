package jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class VehicleDtoJsonTest {

	private final ObjectMapper mapper = JsonMapper.builder()
			.addModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
			.build();

	@Test
	void saveRequestPreservesAllExistingJsonPropertyNames() throws Exception {
		String json = """
				{
				  "display_name": "テスト車両",
				  "registration_number": "品川 300 あ 12-34",
				  "chassis_number": "TEST-001",
				  "vehicle_identification_code": "IDENT-001",
				  "vehicle_name": "テスト車",
				  "model_code": "MODEL-001",
				  "first_registration_month": "2023-11",
				  "inspection_expiry_date": "2026-11-05",
				  "issued_date": "2023-11-06",
				  "usage_category": "PRIVATE",
				  "purpose": "COMMUTE",
				  "notes": "確認済み"
				}
				""";

		VehicleSaveRequest request = mapper.readValue(json, VehicleSaveRequest.class);

		assertThat(request.getDisplayName()).isEqualTo("テスト車両");
		assertThat(request.getInspectionExpiryDate()).isEqualTo(LocalDate.of(2026, 11, 5));
		assertThat(mapper.readTree(mapper.writeValueAsString(request))).isEqualTo(mapper.readTree(json));
	}

	@Test
	void detailPreservesSnakeCaseFieldsAndCamelCaseTimestamps() throws Exception {
		LocalDateTime timestamp = LocalDateTime.of(2026, 10, 20, 12, 30);
		VehicleDetailDto detail = VehicleDetailDto.builder()
				.displayName("テスト車両").registrationNumber("TEST-123").chassisNumber("CHASSIS-001")
				.vehicleIdentificationCode("IDENT-001").vehicleName("テスト車").modelCode("MODEL-001")
				.firstRegistrationMonth("2023-11").inspectionExpiryDate(LocalDate.of(2026, 11, 5))
				.issuedDate(LocalDate.of(2023, 11, 6)).usageCategory("PRIVATE").daysRemaining(16L)
				.createdBy("creator").updatedBy("updater").createdAt(timestamp).updatedAt(timestamp).build();

		JsonNode actual = mapper.readTree(mapper.writeValueAsString(detail));
		JsonNode expected = mapper.readTree("""
				{
				  "id": null,
				  "display_name": "テスト車両",
				  "registration_number": "TEST-123",
				  "chassis_number": "CHASSIS-001",
				  "vehicle_identification_code": "IDENT-001",
				  "vehicle_name": "テスト車",
				  "model_code": "MODEL-001",
				  "first_registration_month": "2023-11",
				  "inspection_expiry_date": "2026-11-05",
				  "issued_date": "2023-11-06",
				  "usage_category": "PRIVATE",
				  "purpose": null,
				  "status": null,
				  "days_remaining": 16,
				  "notes": null,
				  "created_by": "creator",
				  "updated_by": "updater",
				  "createdAt": "2026-10-20T12:30:00",
				  "updatedAt": "2026-10-20T12:30:00"
				}
				""");

		assertThat(actual).isEqualTo(expected);
	}

	@Test
	void historyPreservesExistingJsonPropertyNames() throws Exception {
		VehicleInspectionHistoryDto history = VehicleInspectionHistoryDto.builder()
				.id(1L).vehicleId(2L).changeType("EXPIRY_UPDATED")
				.previousExpiryDate(LocalDate.of(2026, 11, 5)).newExpiryDate(LocalDate.of(2028, 11, 5))
				.changedBy("operator").changedAt(LocalDateTime.of(2026, 10, 20, 12, 30)).build();

		assertThat(mapper.readTree(mapper.writeValueAsString(history))).isEqualTo(mapper.readTree("""
				{
				  "id": 1,
				  "vehicle_id": 2,
				  "change_type": "EXPIRY_UPDATED",
				  "previous_expiry_date": "2026-11-05",
				  "new_expiry_date": "2028-11-05",
				  "changed_by": "operator",
				  "changedAt": "2026-10-20T12:30:00"
				}
				"""));
	}
}
