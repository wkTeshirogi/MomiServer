package jp.xoki.web.zunda.smarthomewebserverbackend.item.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.model.InventoryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.constants.ItemMessageConstants;

/** 実在庫の登録または更新に利用するリクエストDTO。 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemStockRegistRequest {

	/** 更新時に指定する実在庫ID。新規登録時は未指定。 */
	private Long id;

	/** 実在庫が紐づく在庫品目ID。 */
	@NotNull(message = ItemMessageConstants.ITEM_STOCK_INVENTORY_ITEM_ID_REQUIRED)
	@JsonProperty("inventory_item_id")
	private Long inventoryItemId;

	/** 実在庫を保管している場所。 */
	@NotBlank(message = ItemMessageConstants.ITEM_STOCK_STORAGE_LOCATION_REQUIRED)
	@Size(max = 255, message = ItemMessageConstants.ITEM_STOCK_STORAGE_LOCATION_SIZE)
	@JsonProperty("storage_location")
	private String storageLocation;

	/** 現在の在庫数量。 */
	@NotNull(message = ItemMessageConstants.ITEM_STOCK_QUANTITY_REQUIRED)
	@Min(value = 0, message = ItemMessageConstants.ITEM_STOCK_QUANTITY_MIN)
	private Integer quantity;

	/** 補充時に推奨する数量。 */
	@Min(value = 0, message = ItemMessageConstants.ITEM_STOCK_REPLENISH_QUANTITY_MIN)
	@JsonProperty("replenish_quantity")
	private Integer replenishQuantity;

	/** 現在の在庫状態。 */
	@NotNull(message = ItemMessageConstants.ITEM_STOCK_INVENTORY_STATUS_REQUIRED)
	@JsonProperty("inventory_status")
	private InventoryStatus inventoryStatus;

	/** 消費期限または利用期限。 */
	@JsonProperty("expiration_date")
	private LocalDate expirationDate;

	/** 実在庫に関する補足メモ。 */
	private String memo;
}
