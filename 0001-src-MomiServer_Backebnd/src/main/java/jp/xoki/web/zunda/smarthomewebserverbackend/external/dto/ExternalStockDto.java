package jp.xoki.web.zunda.smarthomewebserverbackend.external.dto;

/** 外部連携へ公開する在庫情報。
 *
 * 内部メモや仕入先情報は含めない。
 * @param id 実在庫ID
 * @param inventoryItemId 品目ID
 * @param itemName 品目名
 * @param manufacturer メーカー
 * @param modelNumber 型番
 * @param category カテゴリ
 * @param storageLocation 保管場所
 * @param quantity 現在数量
 * @param minimumQuantity 最低数量
 * @param unit 単位
 * @param inventoryStatus 在庫状態
 */
public record ExternalStockDto(long id, long inventoryItemId, String itemName, String manufacturer,
		String modelNumber, String category, String storageLocation, int quantity,
		int minimumQuantity, String unit, String inventoryStatus) {
}
