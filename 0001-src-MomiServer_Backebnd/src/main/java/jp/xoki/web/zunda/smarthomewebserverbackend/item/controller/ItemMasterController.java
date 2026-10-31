package jp.xoki.web.zunda.smarthomewebserverbackend.item.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.security.annotation.AdminOnly;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.dto.ItemMasterDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.dto.ItemMasterRegistRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.service.ItemMasterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
@RequestMapping("/api/master/item")
@RequiredArgsConstructor
@Tag(name = "ItemMasterController", description = "在庫品目マスタ関連のAPI")
public class ItemMasterController {

	private final ItemMasterService itemMasterService;

	/**
	 * 在庫品目マスタに登録されているデータを取得
 	 * @return
	 */
	@PostMapping("/list")
	@Operation(summary = "アイテム一覧取得", description = "すべてのアイテム情報を返します", operationId = "getItemMasterList")
	public ResponseEntity<List<ItemMasterDetailDto>> list(){
		return ResponseEntity.ok(itemMasterService.list());
	}

	/**
	 * 在庫品目マスタに登録されているデータを取得
	 * @param id
	 * @return
	 */
	@PostMapping("/detail")
	@Operation(summary = "アイテム詳細取得", description = "指定されたIDのアイテム情報を返します", operationId = "getItemMasterDetail")
	public ResponseEntity<ItemMasterDetailDto> getItemMasterDetail(@RequestParam Long id){
		return ResponseEntity.ok(itemMasterService.detail(id));
	}

	/**
	 * 在庫品目マスタにデータを登録
	 * @param itemMasterRegistRequest
	 * @return
	 */
	@PostMapping("/register")
	@AdminOnly
	@Operation(summary = "アイテム登録", description = "新しいアイテムを登録します", operationId = "registerItemMaster")
	public ResponseEntity<ItemMasterDetailDto> register(@Valid @RequestBody ItemMasterRegistRequest itemMasterRegistRequest){
		return ResponseEntity.ok(itemMasterService.save(itemMasterRegistRequest));
	}

}
