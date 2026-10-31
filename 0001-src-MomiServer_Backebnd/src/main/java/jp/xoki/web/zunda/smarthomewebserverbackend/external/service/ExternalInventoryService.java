package jp.xoki.web.zunda.smarthomewebserverbackend.external.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.pagination.Pagination;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.security.TokenHashes;
import java.util.List;
import java.util.UUID;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.OptimisticLockConflictException;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.ResourceNotFoundException;
import jp.xoki.web.zunda.smarthomewebserverbackend.external.dto.ExternalStockDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.external.dto.ExternalStockOperationRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.external.repository.ExternalInventoryRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.dto.ItemStockOperationRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.model.InventoryStockChangeType;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.service.ItemStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static jp.xoki.web.zunda.smarthomewebserverbackend.external.constants.ExternalInventoryServiceConstants.MAX_LIMIT;
import static jp.xoki.web.zunda.smarthomewebserverbackend.external.constants.ExternalInventoryServiceConstants.OPERATIONS;

/** 外部在庫連携。既存の数量操作・履歴・通知と同一トランザクションで処理する。 */
@Service
@RequiredArgsConstructor
public class ExternalInventoryService {
	private final ExternalInventoryRepository repository;
	private final ItemStockService stocks;
	private final ObjectMapper mapper;

	/** 限定件数の一覧。
	 * @param limit 件数
	 * @param offset 開始位置
	 * @return 在庫一覧
	 */
	public List<ExternalStockDto> list(int limit, int offset) {
		Pagination.requireLimit(limit, MAX_LIMIT, "limit must be 1..100 and offset must be nonnegative");
		Pagination.requireOffset(offset, "limit must be 1..100 and offset must be nonnegative");
		return repository.list(limit, offset);
	}

	/** 詳細取得。
	 * @param id 実在庫ID
	 * @return 在庫詳細
	 */
	public ExternalStockDto detail(long id) {
		return repository.find(id).orElseThrow(() -> new ResourceNotFoundException("Stock", id));
	}

	/** 重複防止付き数量操作。
	 *
	 * 同一キーと操作IDで内容が同一なら初回応答を返し、異なる場合は競合にする。
	 * @param keyId APIキーID
	 * @param id 実在庫ID
	 * @param request 操作内容
	 * @return 初回の操作結果
	 */
	@Transactional
	public ExternalStockDto operate(UUID keyId, long id, ExternalStockOperationRequest request) {
		if (request.changeType() == null || !OPERATIONS.contains(request.changeType())
				|| request.quantity() == null || request.quantity() < 0) {
			throw new IllegalArgumentException("Invalid inventory operation");
		}

		// 数量だけでなく対象在庫と要求全体を照合し、同じ操作IDを別の処理へ使い回すことを防ぐ。
		String hash = ExternalSecrets.hash(id + ":" + json(request));

		// 在庫を更新する前に操作IDを確保する。再送は保存済み応答を返し、数量操作を繰り返さない。
		if (!repository.reserve(keyId, request.operationId(), hash)) {
			ExternalInventoryRepository.SavedOperation previous = repository.previous(keyId, request.operationId());

			// 固定長ハッシュの一致位置による比較時間の差を避け、欠損値は従来どおり競合にする。
			if (!TokenHashes.matches(hash, previous.requestHash())
					|| previous.response() == null) {
				throw new OptimisticLockConflictException(
						"Operation ID has already been used with different input");
			}
			try {
				return mapper.readValue(previous.response(), ExternalStockDto.class);
			} catch (JsonProcessingException exception) {
				throw new IllegalStateException("Stored inventory operation is invalid", exception);
			}
		}

		// 入庫の上限確認と実際の数量操作の間で、別要求が同じ在庫数量を変更しないよう行ロックする。
		ExternalStockDto before = repository.lock(id)
				.orElseThrow(() -> new ResourceNotFoundException("Stock", id));
		if (InventoryStockChangeType.INBOUND.getStoredName().equals(request.changeType())
				&& request.quantity() > Integer.MAX_VALUE - before.quantity()) {
			throw new IllegalArgumentException("Stock quantity exceeds the supported range");
		}
		stocks.operate(ItemStockOperationRequest.builder().inventoryStockId(id)
				.changeType(InventoryStockChangeType.from(request.changeType()))
				.quantity(request.quantity()).reason(request.reason())
				.memo("external:" + keyId + "/" + request.operationId()).build());
		ExternalStockDto response = detail(id);

		// 在庫・履歴と初回応答を同一トランザクションに残し、応答が失われても再送時に同じ結果を返せるようにする。
		repository.complete(keyId, request.operationId(), json(response));
		return response;
	}

	private String json(Object value) {
		try {
			return mapper.writeValueAsString(value);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Inventory operation serialization failed", exception);
		}
	}
}
