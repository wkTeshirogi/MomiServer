package jp.xoki.web.zunda.smarthomewebserverbackend.item.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.model.InventoryStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 実在庫詳細を返却するDTO。 */
@Getter
@Builder
public class ItemStockDetailDto {

	/** 実在庫ID。 */
	private Long id;

	/** 紐づく在庫品目ID。 */
	@JsonProperty("inventory_item_id")
	private Long inventoryItemId;

	/** 保管場所。 */
	@JsonProperty("storage_location")
	private String storageLocation;

	/** 現在数量。 */
	private Integer quantity;

	/** 補充時の推奨数量。 */
	@JsonProperty("replenish_quantity")
	private Integer replenishQuantity;

	/** 現在の在庫状態。 */
	@JsonProperty("inventory_status")
	private InventoryStatus inventoryStatus;

	/** 消費期限または利用期限。 */
	@JsonProperty("expiration_date")
	private LocalDate expirationDate;

	/** 補足メモ。 */
	private String memo;

	/** 作成日時。 */
	private LocalDateTime createdAt;

	/** 更新日時。 */
	private LocalDateTime updatedAt;
}
