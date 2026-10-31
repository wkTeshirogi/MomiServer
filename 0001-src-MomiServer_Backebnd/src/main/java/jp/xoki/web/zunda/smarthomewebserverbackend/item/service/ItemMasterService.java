package jp.xoki.web.zunda.smarthomewebserverbackend.item.service;

import jp.xoki.web.zunda.jooq.tables.records.MInventoryItemsRecord;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.dto.ItemMasterDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.dto.ItemMasterRegistRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.repository.ItemMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** 在庫品目マスタの参照、登録、更新を扱うService。 */
@Service
@RequiredArgsConstructor
public class ItemMasterService {

	/** 在庫品目マスタRepository。 */
	private final ItemMasterRepository itemMasterRepository;

	/**
	 * 在庫品目マスタデータの一覧を取得
	 *
	 * @return
	 */
	public List<ItemMasterDetailDto> list(){
		return itemMasterRepository.findAll().stream()
				.map(this::toDetailDto)
				.toList();
	}

	/**
	 * 在庫品目マスタデータの詳細を取得
	 *
	 * @param id
	 * @return
	 */
	public ItemMasterDetailDto detail(Long id){
		return itemMasterRepository.findById(id)
				.map(this::toDetailDto)
				.orElseThrow(() -> new IllegalArgumentException("Item not found"));
	}

	/**
	 * データのIDが存在する場合は更新、存在しない場合は新規登録を行う。
	 *
	 * @param itemMasterRegistRequest
	 * @return
	 */
	public ItemMasterDetailDto save(ItemMasterRegistRequest itemMasterRegistRequest){

		// IDが指定されている場合は既存在庫品目の更新として扱う。
		if(itemMasterRegistRequest.getId() != null && itemMasterRegistRequest.getId() > 0){
			// 更新件数取得/データ更新
			int updatedCount = itemMasterRepository.update(toRecord(itemMasterRegistRequest));

			// 更新件数が0の場合は対象在庫品目が存在しない。
			if(updatedCount == 0){
				throw new IllegalArgumentException("Item not found");
			}
			return detail(itemMasterRegistRequest.getId());
		}else{
			Long savedId = itemMasterRepository.save(toRecord(itemMasterRegistRequest));
			return detail(savedId);
		}
	}


	/**
	 * レコードからDtoへの変換
	 *
	 * @param itemMaster
	 * @return
	 */
	private ItemMasterDetailDto toDetailDto(MInventoryItemsRecord itemMaster){
		return ItemMasterDetailDto.builder()
				.id(itemMaster.getId())
				.itemName(itemMaster.getItemName())
				.category(itemMaster.getCategory())
				.status(itemMaster.getStatus())
				.unit(itemMaster.getUnit())
				.minQuantity(itemMaster.getMinQuantity())
				.manufacturer(itemMaster.getManufacturer())
				.modelNumber(itemMaster.getModelNumber())
				.janCode(itemMaster.getJanCode())
				.purchaseUrl(itemMaster.getPurchaseUrl())
				.memo(itemMaster.getMemo())
				.createdAt(itemMaster.getCreatedDt())
				.updatedAt(itemMaster.getUpdatedDt())
				.build();
	}

	/**
	 * Requestからレコードヘ変換
	 *
	 * @param itemMaster
	 * @return
	 */
	private MInventoryItemsRecord toRecord(ItemMasterRegistRequest itemMaster){
		MInventoryItemsRecord databaseRecord = new MInventoryItemsRecord();
		databaseRecord.setId(itemMaster.getId());
		databaseRecord.setItemName(itemMaster.getItemName());
		databaseRecord.setCategory(itemMaster.getCategory());
		databaseRecord.setStatus(itemMaster.getStatus());
		databaseRecord.setUnit(itemMaster.getUnit());
		databaseRecord.setMinQuantity(itemMaster.getMinQuantity());
		databaseRecord.setManufacturer(itemMaster.getManufacturer());
		databaseRecord.setModelNumber(itemMaster.getModelNumber());
		databaseRecord.setJanCode(itemMaster.getJanCode());
		databaseRecord.setPurchaseUrl(itemMaster.getPurchaseUrl());
		databaseRecord.setMemo(itemMaster.getMemo());
		return databaseRecord;
	}
}
