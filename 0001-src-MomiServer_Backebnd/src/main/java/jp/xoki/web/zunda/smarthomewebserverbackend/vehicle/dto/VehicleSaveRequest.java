package jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.constants.VehicleMessageConstants;

/** 車両の新規登録または更新に利用するリクエストDTO。 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleSaveRequest {

	@NotBlank(message = VehicleMessageConstants.VEHICLE_DISPLAY_NAME_REQUIRED)
	@Size(max = 100, message = VehicleMessageConstants.VEHICLE_DISPLAY_NAME_SIZE)
	@JsonProperty("display_name")
	private String displayName;

	@NotBlank(message = VehicleMessageConstants.VEHICLE_REGISTRATION_NUMBER_REQUIRED)
	@Size(max = 50, message = VehicleMessageConstants.VEHICLE_REGISTRATION_NUMBER_SIZE)
	@JsonProperty("registration_number")
	private String registrationNumber;

	@NotBlank(message = VehicleMessageConstants.VEHICLE_CHASSIS_NUMBER_REQUIRED)
	@Size(max = 100, message = VehicleMessageConstants.VEHICLE_CHASSIS_NUMBER_SIZE)
	@JsonProperty("chassis_number")
	private String chassisNumber;

	@Size(max = 100, message = VehicleMessageConstants.VEHICLE_VEHICLE_IDENTIFICATION_CODE_SIZE)
	@JsonProperty("vehicle_identification_code")
	private String vehicleIdentificationCode;

	@NotBlank(message = VehicleMessageConstants.VEHICLE_VEHICLE_NAME_REQUIRED)
	@Size(max = 100, message = VehicleMessageConstants.VEHICLE_VEHICLE_NAME_SIZE)
	@JsonProperty("vehicle_name")
	private String vehicleName;

	@Size(max = 100, message = VehicleMessageConstants.VEHICLE_MODEL_CODE_SIZE)
	@JsonProperty("model_code")
	private String modelCode;

	@Pattern(regexp = "^$|^\\d{4}-(0[1-9]|1[0-2])$", message = VehicleMessageConstants.VEHICLE_FIRST_REGISTRATION_MONTH_PATTERN)
	@JsonProperty("first_registration_month")
	private String firstRegistrationMonth;

	@NotNull(message = VehicleMessageConstants.VEHICLE_INSPECTION_EXPIRY_DATE_REQUIRED)
	@JsonProperty("inspection_expiry_date")
	private LocalDate inspectionExpiryDate;

	@JsonProperty("issued_date")
	private LocalDate issuedDate;

	@Size(max = 50, message = VehicleMessageConstants.VEHICLE_USAGE_CATEGORY_SIZE)
	@JsonProperty("usage_category")
	private String usageCategory;

	@Size(max = 50, message = VehicleMessageConstants.VEHICLE_PURPOSE_SIZE)
	private String purpose;

	@Size(max = 2000, message = VehicleMessageConstants.VEHICLE_NOTES_SIZE)
	private String notes;
}
