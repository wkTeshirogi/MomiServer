package jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** 車検更新専用の入力。交付年月日を省略した場合は登録済みの値を維持する。 */
public record VehicleInspectionRenewalRequest(

		@NotNull @JsonProperty("previous_expiry_date") LocalDate previousExpiryDate,
		@NotNull @JsonProperty("inspection_expiry_date") LocalDate inspectionExpiryDate,
		@JsonProperty("issued_date") LocalDate issuedDate) {
}
