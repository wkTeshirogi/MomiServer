package jp.xoki.web.zunda.smarthomewebserverbackend.item.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ItemMasterDetailDto {
	private Long id;
	@JsonProperty("item_name")
	private String itemName;
	private String category;
	private String unit;
	@JsonProperty("min_quantity")
	private Integer minQuantity;
	private String manufacturer;
	@JsonProperty("model_number")
	private String modelNumber;
	@JsonProperty("jan_code")
	private String janCode;
	@JsonProperty("purchase_url")
	private String purchaseUrl;
	private String status;
	private String memo;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
}
