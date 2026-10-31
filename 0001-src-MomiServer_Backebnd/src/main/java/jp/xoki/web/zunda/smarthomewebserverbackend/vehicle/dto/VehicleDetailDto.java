package jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.model.VehicleInspectionStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 車両と現在の車検期限状態を返却するDTO。 */
@Getter
@Builder
public class VehicleDetailDto {

	private Long id;
	@JsonProperty("display_name")
	private String displayName;
	@JsonProperty("registration_number")
	private String registrationNumber;
	@JsonProperty("chassis_number")
	private String chassisNumber;
	@JsonProperty("vehicle_identification_code")
	private String vehicleIdentificationCode;
	@JsonProperty("vehicle_name")
	private String vehicleName;
	@JsonProperty("model_code")
	private String modelCode;
	@JsonProperty("first_registration_month")
	private String firstRegistrationMonth;
	@JsonProperty("inspection_expiry_date")
	private LocalDate inspectionExpiryDate;
	@JsonProperty("issued_date")
	private LocalDate issuedDate;
	@JsonProperty("usage_category")
	private String usageCategory;
	private String purpose;
	private VehicleInspectionStatus status;
	@JsonProperty("days_remaining")
	private Long daysRemaining;
	private String notes;
	@JsonProperty("created_by")
	private String createdBy;
	@JsonProperty("updated_by")
	private String updatedBy;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
}
