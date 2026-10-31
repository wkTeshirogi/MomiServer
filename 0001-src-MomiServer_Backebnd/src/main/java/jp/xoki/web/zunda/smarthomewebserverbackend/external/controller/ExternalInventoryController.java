package jp.xoki.web.zunda.smarthomewebserverbackend.external.controller;

import jakarta.validation.Valid;
import jp.xoki.web.zunda.smarthomewebserverbackend.external.dto.ExternalStockDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.external.dto.ExternalStockOperationRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.external.service.ExternalInventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** 在庫参照・数量操作のみを公開する外部API v1。 */
@RestController
@RequestMapping("/api/v1/external/inventory/stocks")
@RequiredArgsConstructor
public class ExternalInventoryController {
	private final ExternalInventoryService service;

	/** 一覧。
	 * @param limit 件数。既定20、最大100
	 * @param offset 開始位置
	 * @return 在庫一覧
	 */
	@GetMapping
	@PreAuthorize("hasAuthority('SCOPE_inventory:read')")
	public List<ExternalStockDto> list(@RequestParam(defaultValue = "20") int limit,
			@RequestParam(defaultValue = "0") int offset) {
		return service.list(limit, offset);
	}

	/** 詳細。
	 * @param id 実在庫ID
	 * @return 在庫情報
	 */
	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('SCOPE_inventory:read')")
	public ExternalStockDto detail(@PathVariable long id) {
		return service.detail(id);
	}

	/** 入庫・出庫・棚卸し。
	 * @param id 実在庫ID
	 * @param request 数量操作
	 * @param keyId 認証フィルターで確認したキーID
	 * @return 操作結果
	 */
	@PostMapping("/{id}/operations")
	@PreAuthorize("hasAuthority('SCOPE_inventory:write')")
	public ExternalStockDto operate(@PathVariable long id,
			@Valid @RequestBody ExternalStockOperationRequest request,
			@RequestAttribute("external.apiKeyId") UUID keyId) {
		return service.operate(keyId, id, request);
	}
}
