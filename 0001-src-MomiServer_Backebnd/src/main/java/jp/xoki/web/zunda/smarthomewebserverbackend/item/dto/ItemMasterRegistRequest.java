package jp.xoki.web.zunda.smarthomewebserverbackend.item.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.constants.ItemMessageConstants;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemMasterRegistRequest {
	private Long id;

	@NotBlank(message = ItemMessageConstants.ITEM_MASTER_ITEM_NAME_REQUIRED)
	@Size(max = 255, message = ItemMessageConstants.ITEM_MASTER_ITEM_NAME_SIZE)
	@JsonProperty("item_name")
	private String itemName;

	@NotBlank(message = ItemMessageConstants.ITEM_MASTER_CATEGORY_REQUIRED)
	@Size(max = 100, message = ItemMessageConstants.ITEM_MASTER_CATEGORY_SIZE)
	private String category;

	@NotBlank(message = ItemMessageConstants.ITEM_MASTER_UNIT_REQUIRED)
	@Size(max = 50, message = ItemMessageConstants.ITEM_MASTER_UNIT_SIZE)
	private String unit;

	@NotNull(message = ItemMessageConstants.ITEM_MASTER_MIN_QUANTITY_REQUIRED)
	@Min(value = 0, message = ItemMessageConstants.ITEM_MASTER_MIN_QUANTITY_MIN)
	@JsonProperty("min_quantity")
	private Integer minQuantity;

	@Size(max = 255, message = ItemMessageConstants.ITEM_MASTER_MANUFACTURER_SIZE)
	private String manufacturer;

	@Size(max = 255, message = ItemMessageConstants.ITEM_MASTER_MODEL_NUMBER_SIZE)
	@JsonProperty("model_number")
	private String modelNumber;

	@Size(max = 20, message = ItemMessageConstants.ITEM_MASTER_JAN_CODE_SIZE)
	@JsonProperty("jan_code")
	private String janCode;

	@JsonProperty("purchase_url")
	private String purchaseUrl;

	@NotBlank(message = ItemMessageConstants.ITEM_MASTER_STATUS_REQUIRED)
	@Size(max = 50, message = ItemMessageConstants.ITEM_MASTER_STATUS_SIZE)
	private String status;

	private String memo;
}
