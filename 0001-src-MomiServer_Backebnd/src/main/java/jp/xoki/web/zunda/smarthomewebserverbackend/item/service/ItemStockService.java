package jp.xoki.web.zunda.smarthomewebserverbackend.item.service;

import static jp.xoki.web.zunda.smarthomewebserverbackend.item.constants.ItemStockServiceConstants.STOCK_NOT_FOUND;


import jp.xoki.web.zunda.jooq.tables.records.TInventoryStockHistoriesRecord;
import jp.xoki.web.zunda.jooq.tables.records.TInventoryStocksRecord;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.BrowserNotificationCreateDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.message.MessageCatalog;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.message.MessageCode;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationLevel;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationSourceType;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.model.NotificationType;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.util.Notifications;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.dto.ItemStockDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.dto.ItemStockHistoryDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.dto.ItemStockOperationRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.dto.ItemStockRegistRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.model.InventoryStatus;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.model.InventoryStockChangeType;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.repository.ItemStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** 実在庫の登録、数量操作、履歴参照を扱うService。 */
@Service
@RequiredArgsConstructor
public class ItemStockService {

	/** 実在庫と数量変更履歴のRepository。 */
	private final ItemStockRepository itemStockRepository;

	/** アプリケーション通知の送信窓口。 */
	private final Notifications notifications;

	/** バックエンド共通メッセージカタログ。 */
	private final MessageCatalog messageCatalog;

	/**
	 * 実在庫IDに一致する実在庫詳細を取得する。
	 *
	 * @param id 実在庫ID
	 * @return 実在庫詳細
	 */
	public ItemStockDetailDto detail(Long id){
		return itemStockRepository.findById(id).map(this::toDetailDto).orElseThrow(() -> new IllegalArgumentException(STOCK_NOT_FOUND));
	}

	/**
	 * 実在庫を一覧取得する。
	 *
	 * @return 実在庫詳細一覧
	 */
	public List<ItemStockDetailDto> list(){
		return itemStockRepository.findAll().stream()
				.map(this::toDetailDto)
				.toList();
	}

	/**
	 * 実在庫を登録または更新する。
	 *
	 * @param itemStockRegistRequest 登録または更新内容
	 * @return 保存後の実在庫詳細
	 */
	@Transactional
	public ItemStockDetailDto save(ItemStockRegistRequest itemStockRegistRequest){

		// 最低数量は品目マスタを正とし、画面から渡された在庫状態だけでは警告・在庫切れを決定しない。
		Integer minimumQuantity = itemStockRepository.findMinimumQuantityByItemId(
				itemStockRegistRequest.getInventoryItemId());
		InventoryStatus inventoryStatus = calculateStatus(
				itemStockRegistRequest.getInventoryStatus() == InventoryStatus.DELETED,
				itemStockRegistRequest.getQuantity(),
				minimumQuantity);
		TInventoryStocksRecord databaseRecord = toRecord(itemStockRegistRequest, inventoryStatus);
		String statusBefore = null;
		Long savedId;

		// IDが指定されている場合は既存実在庫の更新として扱う。
		if(itemStockRegistRequest.getId() != null && itemStockRegistRequest.getId() > 0){
			TInventoryStocksRecord currentStock = itemStockRepository.findByIdForUpdate(itemStockRegistRequest.getId())
					.orElseThrow(() -> new IllegalArgumentException(STOCK_NOT_FOUND));
			statusBefore = currentStock.getInventoryStatus();
			int updatedCount = itemStockRepository.update(databaseRecord);

			// 更新件数が0の場合は対象実在庫が存在しないためエラーにする。
			if(updatedCount == 0){
				throw new IllegalArgumentException(STOCK_NOT_FOUND);
			}
			savedId = itemStockRegistRequest.getId();
		}else{
			savedId = itemStockRepository.save(databaseRecord);
		}

		notifyInventoryStatusIfNeeded(
				itemStockRegistRequest.getInventoryItemId(),
				itemStockRegistRequest.getQuantity(),
				statusBefore,
				inventoryStatus);
		return detail(savedId);
	}

	/**
	 * 実在庫の数量操作を行い、数量変更履歴を保存する。
	 *
	 * @param request 数量操作内容
	 * @return 操作後の実在庫詳細
	 */
	@Transactional
	public ItemStockDetailDto operate(ItemStockOperationRequest request){

		// 操作前数量を行ロック下で読み、並行する入出庫による数量更新の取りこぼしを防ぐ。
		TInventoryStocksRecord stock = itemStockRepository.findByIdForUpdate(request.getInventoryStockId())
				.orElseThrow(() -> new IllegalArgumentException(STOCK_NOT_FOUND));

		// 最小在庫数取得
		Integer minimumQuantity = itemStockRepository.findMinimumQuantityByItemId(stock.getInventoryItemId());

		// 操作前数量取得(null回避のためif記載)
		int quantityBefore = stock.getQuantity() == null ? 0 : stock.getQuantity();
		String statusBefore = stock.getInventoryStatus();

		// 操作数量取得(null回避のためif記載)
		int operationQuantity = request.getQuantity() == null ? 0 : request.getQuantity();

		// 操作後数量取得
		int quantityAfter = calculateQuantityAfter(request.getChangeType(), quantityBefore, operationQuantity);

		// 在庫状態取得
		InventoryStatus statusAfter = calculateStatus(
				request.getChangeType() == InventoryStockChangeType.DELETE,
				quantityAfter,
				minimumQuantity);

		// 差分取得
		int quantityDelta = quantityAfter - quantityBefore;

		// 更新件数
		int updatedCount = itemStockRepository.updateQuantityAndStatus(stock.getId(), quantityAfter, statusAfter.getStoredName());

		// 行ロック後でも更新件数が0の場合は対象が削除または不整合状態になっている。
		if(updatedCount == 0){
			throw new IllegalArgumentException(STOCK_NOT_FOUND);
		}

		/** 数量変更履歴を保存 */
		itemStockRepository.saveHistory(toHistoryRecord(request, quantityDelta, quantityBefore, quantityAfter, statusBefore, statusAfter));

		/** 通知処理 */
		notifyInventoryStatusIfNeeded(stock.getInventoryItemId(), quantityAfter, statusBefore, statusAfter);
		return detail(stock.getId());
	}

	/**
	 * 実在庫IDに紐づく数量変更履歴を取得する。
	 *
	 * @param inventoryStockId 実在庫ID
	 * @return 数量変更履歴一覧
	 */
	public List<ItemStockHistoryDetailDto> history(Long inventoryStockId){
		return itemStockRepository.findHistoriesByStockId(inventoryStockId).stream()
				.map(this::toHistoryDetailDto)
				.toList();
	}

	/**
	 * 実在庫レコードを画面/API返却用DTOへ変換する。
	 *
	 * @param record 実在庫レコード
	 * @return 実在庫詳細DTO
	 */
	private ItemStockDetailDto toDetailDto(TInventoryStocksRecord databaseRecord) {
		return ItemStockDetailDto.builder()
				.id(databaseRecord.getId())
				.inventoryItemId(databaseRecord.getInventoryItemId())
				.storageLocation(databaseRecord.getStorageLocation())
				.quantity(databaseRecord.getQuantity())
				.replenishQuantity(databaseRecord.getReplenishQuantity())
				.inventoryStatus(InventoryStatus.from(databaseRecord.getInventoryStatus()))
				.expirationDate(databaseRecord.getExpirationDate())
				.memo(databaseRecord.getMemo())
				.createdAt(databaseRecord.getCreatedDt())
				.updatedAt(databaseRecord.getUpdatedDt())
				.build();
	}

	/**
	 * 数量変更履歴レコードを画面/API返却用DTOへ変換する。
	 *
	 * @param record 数量変更履歴レコード
	 * @return 数量変更履歴DTO
	 */
	private ItemStockHistoryDetailDto toHistoryDetailDto(TInventoryStockHistoriesRecord databaseRecord) {
		return ItemStockHistoryDetailDto.builder()
				.id(databaseRecord.getId())
				.inventoryStockId(databaseRecord.getInventoryStockId())
				.changeType(InventoryStockChangeType.from(databaseRecord.getChangeType()))
				.quantityDelta(databaseRecord.getQuantityDelta())
				.quantityBefore(databaseRecord.getQuantityBefore())
				.quantityAfter(databaseRecord.getQuantityAfter())
				.inventoryStatusBefore(InventoryStatus.from(databaseRecord.getInventoryStatusBefore()))
				.inventoryStatusAfter(InventoryStatus.from(databaseRecord.getInventoryStatusAfter()))
				.reason(databaseRecord.getReason())
				.memo(databaseRecord.getMemo())
				.changedAt(databaseRecord.getChangedDt())
				.createdAt(databaseRecord.getCreatedDt())
				.build();
	}

	/**
	 * 実在庫登録リクエストをDBレコードへ変換する。
	 *
	 * @param request 実在庫登録リクエスト
	 * @param inventoryStatus 数量と最低数量から判定した在庫状態
	 * @return 実在庫レコード
	 */
	private TInventoryStocksRecord toRecord(ItemStockRegistRequest request, InventoryStatus inventoryStatus) {
		// 実在庫レコード宣言
		TInventoryStocksRecord databaseRecord = new TInventoryStocksRecord();
		databaseRecord.setId(request.getId());
		databaseRecord.setInventoryItemId(request.getInventoryItemId());
		databaseRecord.setStorageLocation(request.getStorageLocation());
		databaseRecord.setQuantity(request.getQuantity());
		databaseRecord.setReplenishQuantity(request.getReplenishQuantity());
		databaseRecord.setInventoryStatus(inventoryStatus.getStoredName());
		databaseRecord.setExpirationDate(request.getExpirationDate());
		databaseRecord.setMemo(request.getMemo());
		return databaseRecord;
	}

	/**
	 * 数量操作内容と操作前後の状態から履歴レコードを作成する。
	 *
	 * @param request 数量操作内容
	 * @param quantityDelta 数量差分
	 * @param quantityBefore 操作前数量
	 * @param quantityAfter 操作後数量
	 * @param statusBefore 操作前在庫状態
	 * @param statusAfter 操作後在庫状態
	 * @return 数量変更履歴レコード
	 */
	private TInventoryStockHistoriesRecord toHistoryRecord(ItemStockOperationRequest request, int quantityDelta,
			int quantityBefore, int quantityAfter, String statusBefore, InventoryStatus statusAfter) {
		// 数量変更履歴レコード宣言
		TInventoryStockHistoriesRecord databaseRecord = new TInventoryStockHistoriesRecord();
		databaseRecord.setInventoryStockId(request.getInventoryStockId());
		databaseRecord.setChangeType(request.getChangeType().getStoredName());
		databaseRecord.setQuantityDelta(quantityDelta);
		databaseRecord.setQuantityBefore(quantityBefore);
		databaseRecord.setQuantityAfter(quantityAfter);
		databaseRecord.setInventoryStatusBefore(statusBefore);
		databaseRecord.setInventoryStatusAfter(statusAfter.getStoredName());
		databaseRecord.setReason(request.getReason());
		databaseRecord.setMemo(request.getMemo());
		return databaseRecord;
	}

	/**
	 * 在庫状態が通知対象へ変化した場合に通知ルート設定ベースで通知する。
	 *
	 * @param inventoryItemId 在庫品目ID
	 * @param quantityAfter 操作後数量
	 * @param statusBefore 操作前在庫状態
	 * @param statusAfter 操作後在庫状態
	 */
	private void notifyInventoryStatusIfNeeded(
			Long inventoryItemId,
			int quantityAfter,
			String statusBefore,
			InventoryStatus statusAfter
	) {
		InventoryStatus before = InventoryStatus.from(statusBefore);

		// 注意状態のまま数量だけが変わった場合は再通知せず、LOWから在庫切れへの悪化は別の状態遷移として通知する。
		// 通知対象の在庫状態に変化していない場合は通知しない。
		if (before == statusAfter || !isInventoryNoticeStatus(statusAfter)) {
			return;
		}

		// 通知処理
		notifications.sendNotice(BrowserNotificationCreateDto.builder()
				.notificationType(NotificationType.INVENTORY_LOW)
				.level(toInventoryNoticeLevel(statusAfter))
				.title(toInventoryNoticeTitle(statusAfter))
				.message(toInventoryNoticeMessage(statusAfter, inventoryItemId, quantityAfter))
				.sourceType(NotificationSourceType.INVENTORY)
				.sourceId(String.valueOf(inventoryItemId))
				.linkPath("/stock")
				.build());
	}

	/**
	 * 在庫通知対象の状態かどうかを判定する。
	 *
	 * @param status 在庫状態
	 * @return 在庫通知対象の場合はtrue
	 */
	private boolean isInventoryNoticeStatus(InventoryStatus status) {
		return status == InventoryStatus.LOW
				|| status == InventoryStatus.OUT_OF_STOCK;
	}

	/**
	 * 在庫状態に対応する通知レベルを返す。
	 *
	 * @param status 在庫状態
	 * @return 通知レベル
	 */
	private NotificationLevel toInventoryNoticeLevel(InventoryStatus status) {

		// 在庫切れは警告より高い即時確認対象として扱う。
		if (status == InventoryStatus.OUT_OF_STOCK) {
			return NotificationLevel.CRITICAL;
		}

		return NotificationLevel.WARNING;
	}

	/**
	 * 在庫状態に対応する通知タイトルを返す。
	 *
	 * @param status 在庫状態
	 * @return 通知タイトル
	 */
	private String toInventoryNoticeTitle(InventoryStatus status) {

		// 在庫切れは在庫不足より強い文言で通知する。
		if (status == InventoryStatus.OUT_OF_STOCK) {

			// 在庫切れが発生したことを伝える通知タイトルを送信する。
			return messageCatalog.get(MessageCode.INVENTORY_OUT_OF_STOCK_TITLE);
		}

		// 在庫が少なくなっていることを伝える通知タイトルを送信する。
		return messageCatalog.get(MessageCode.INVENTORY_LOW_TITLE);
	}

	/**
	 * 在庫状態に対応する通知本文を返す。
	 *
	 * @param status 在庫状態
	 * @param inventoryItemId 在庫品目ID
	 * @param quantityAfter 操作後数量
	 * @return 通知本文
	 */
	private String toInventoryNoticeMessage(InventoryStatus status, Long inventoryItemId, int quantityAfter) {
		// 在庫切れは在庫不足より強い文言で通知する。
		MessageCode messageCode = status == InventoryStatus.OUT_OF_STOCK
				? MessageCode.INVENTORY_OUT_OF_STOCK_MESSAGE
				: MessageCode.INVENTORY_LOW_MESSAGE;

		// 在庫品目IDと操作後数量を含む在庫状態通知本文を送信する。
		return messageCatalog.format(messageCode, Map.of(
				"itemId", inventoryItemId,
				"quantity", quantityAfter));
	}

	/**
	 * 操作種別と操作数量から操作後数量を計算する。
	 *
	 * @param changeType 在庫操作種別
	 * @param quantityBefore 操作前数量
	 * @param operationQuantity 操作数量
	 * @return 操作後数量
	 */
	private int calculateQuantityAfter(InventoryStockChangeType changeType, int quantityBefore, int operationQuantity) {
		return switch (changeType) {
			case INBOUND -> quantityBefore + operationQuantity;
			case OUTBOUND, DISCARD -> {

				// 出庫・破棄では在庫数量が負数にならないようにする。
				if(quantityBefore < operationQuantity){
					throw new IllegalArgumentException("Stock quantity cannot be negative");
				}
				yield quantityBefore - operationQuantity;
			}
			case STOCKTAKE -> operationQuantity;
			case DELETE -> quantityBefore;
		};
	}

	/**
	 * 削除状態、操作後数量、最低在庫数から在庫状態を判定する。
	 *
	 * @param deleted 削除状態として保存する場合はtrue
	 * @param quantityAfter 操作後数量
	 * @param minimumQuantity 最低在庫数
	 * @return 操作後在庫状態
	 */
	private InventoryStatus calculateStatus(boolean deleted, int quantityAfter, Integer minimumQuantity) {

		// 削除操作では数量に関係なく削除状態にする。
		if(deleted){
			return InventoryStatus.DELETED;
		}

		// 数量が0の場合は在庫切れ状態にする。
		if(quantityAfter == 0){
			return InventoryStatus.OUT_OF_STOCK;
		}
		int threshold = minimumQuantity == null ? 0 : minimumQuantity;

		// 最低在庫数以下の場合は在庫少状態にする。
		if(quantityAfter <= threshold){
			return InventoryStatus.LOW;
		}
		return InventoryStatus.AVAILABLE;
	}
}
