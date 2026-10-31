package jp.xoki.web.zunda.smarthomewebserverbackend.item.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.dto.ItemStockDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.dto.ItemStockHistoryDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.dto.ItemStockOperationRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.dto.ItemStockRegistRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.service.ItemStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 実在庫の参照、保存、数量操作、履歴取得を提供するController。 */
@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
@Tag(name = "ItemStockController", description = "在庫品目関連のAPI")
public class ItemStockController {

	/** 実在庫の業務処理Service。 */
	private final ItemStockService itemStockService;


	/**
	 * 実在庫IDを指定して実在庫詳細を取得する。
	 *
	 * @param id 実在庫ID
	 * @return 実在庫詳細レスポンス
	 */
	@PostMapping("/detail")
	@Operation(summary = "在庫品目詳細取得", description = "指定されたIDの在庫品目情報を返します", operationId = "getItemStockDetail")
	public ResponseEntity<ItemStockDetailDto> detail(@RequestParam Long id) {
		return ResponseEntity.ok(itemStockService.detail(id));
	}

	/**
	 * 実在庫を一覧取得する。
	 *
	 * @return 実在庫一覧レスポンス
	 */
	@PostMapping("/list")
	@Operation(summary = "在庫品目一覧取得", description = "在庫品目の一覧情報を返します", operationId = "getItemStockList")
	public ResponseEntity<List<ItemStockDetailDto>> list() {
		return ResponseEntity.ok(itemStockService.list());
	}

	/**
	 * 実在庫を登録または更新する。
	 *
	 * @param itemStockRegistRequest 登録または更新内容
	 * @return 保存後の実在庫詳細レスポンス
	 */
	@PostMapping("/save")
	@Operation(summary = "在庫品目保存", description = "在庫品目を保存します", operationId = "saveItemStock")
	public ResponseEntity<ItemStockDetailDto> save(@Valid @RequestBody ItemStockRegistRequest itemStockRegistRequest) {
		return ResponseEntity.ok(itemStockService.save(itemStockRegistRequest));
	}

	/**
	 * 実在庫に対して入庫、出庫、棚卸、破棄、削除操作を行う。
	 *
	 * @param itemStockOperationRequest 数量操作内容
	 * @return 操作後の実在庫詳細レスポンス
	 */
	@PostMapping("/operation")
	@Operation(summary = "実在庫操作", description = "入庫、出庫、棚卸、破棄、削除により実在庫数量を更新します", operationId = "operateItemStock")
	public ResponseEntity<ItemStockDetailDto> operate(@Valid @RequestBody ItemStockOperationRequest itemStockOperationRequest) {
		return ResponseEntity.ok(itemStockService.operate(itemStockOperationRequest));
	}

	/**
	 * 実在庫IDに紐づく数量変更履歴を取得する。
	 *
	 * @param inventoryStockId 実在庫ID
	 * @return 数量変更履歴一覧レスポンス
	 */
	@PostMapping("/history")
	@Operation(summary = "実在庫履歴取得", description = "指定された実在庫の数量変更履歴を返します", operationId = "getItemStockHistory")
	public ResponseEntity<List<ItemStockHistoryDetailDto>> history(@RequestParam Long inventoryStockId) {
		return ResponseEntity.ok(itemStockService.history(inventoryStockId));
	}
}
