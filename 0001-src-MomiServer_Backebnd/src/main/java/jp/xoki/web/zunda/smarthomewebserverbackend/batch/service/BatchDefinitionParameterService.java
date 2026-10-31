package jp.xoki.web.zunda.smarthomewebserverbackend.batch.service;

import jp.xoki.web.zunda.jooq.tables.records.MBatchDefinitionParametersRecord;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchDefinitionParameterDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchDefinitionParameterRegistRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository.BatchDefinitionParameterRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** バッチ定義パラメータマスタの取得、登録、更新、DTO変換を行うService。 */
@Service
@RequiredArgsConstructor
public class BatchDefinitionParameterService {
	private final BatchDefinitionParameterRepository batchDefinitionParameterRepository;

	/**
	 * バッチ定義IDに紐づくパラメータ一覧を取得する。
	 *
	 * @param batchDefinitionId バッチ定義ID
	 * @return バッチ定義パラメータ一覧
	 */
	public List<BatchDefinitionParameterDetailDto> getBatchDefinitionParameters(Long batchDefinitionId) {
		return batchDefinitionParameterRepository.findByBatchDefinitionId(batchDefinitionId).stream()
				.map(this::toDetailDto)
				.toList();
	}

	/**
	 * バッチ定義パラメータ詳細を取得する。
	 *
	 * @param id バッチ定義パラメータID
	 * @return バッチ定義パラメータ詳細
	 */
	public BatchDefinitionParameterDetailDto getBatchDefinitionParameterDetailDto(long id) {
		return batchDefinitionParameterRepository.findById(id).map(this::toDetailDto).orElse(null);
	}

	/**
	 * バッチ定義パラメータを登録または更新する。
	 *
	 * @param request 登録更新リクエスト
	 * @return 登録更新後のバッチ定義パラメータ詳細
	 */
	public BatchDefinitionParameterDetailDto save(BatchDefinitionParameterRegistRequest request) {
		if (request.getId() != null && request.getId() > 0) {
			int updatedCount = batchDefinitionParameterRepository.update(toRecord(request));
			if (updatedCount == 0) {
				throw new ResourceNotFoundException("batch definition parameter", request.getId());
			}
			return getBatchDefinitionParameterDetailDto(request.getId());
		}

		Long savedId = batchDefinitionParameterRepository.save(toRecord(request));
		return getBatchDefinitionParameterDetailDto(savedId);
	}

	private BatchDefinitionParameterDetailDto toDetailDto(MBatchDefinitionParametersRecord databaseRecord) {
		return BatchDefinitionParameterDetailDto.builder()
				.id(databaseRecord.getId())
				.batchDefinitionId(databaseRecord.getBatchDefinitionId())
				.parameterName(databaseRecord.getParameterName())
				.parameterType(databaseRecord.getParameterType())
				.fixedValue(databaseRecord.getFixedValue())
				.dynamicValueType(databaseRecord.getDynamicValueType())
				.valueFormat(databaseRecord.getValueFormat())
				.displayOrder(databaseRecord.getDisplayOrder())
				.enabled(databaseRecord.getEnabled())
				.createdAt(databaseRecord.getCreatedDt())
				.updatedAt(databaseRecord.getUpdatedDt())
				.build();
	}

	private MBatchDefinitionParametersRecord toRecord(BatchDefinitionParameterRegistRequest request) {
		MBatchDefinitionParametersRecord databaseRecord = new MBatchDefinitionParametersRecord();
		databaseRecord.setId(request.getId());
		databaseRecord.setBatchDefinitionId(request.getBatchDefinitionId());
		databaseRecord.setParameterName(request.getParameterName());
		databaseRecord.setParameterType(request.getParameterType());
		databaseRecord.setFixedValue(request.getFixedValue());
		databaseRecord.setDynamicValueType(request.getDynamicValueType());
		databaseRecord.setValueFormat(request.getValueFormat());
		databaseRecord.setDisplayOrder(request.getDisplayOrder());
		databaseRecord.setEnabled(request.getEnabled());
		return databaseRecord;
	}
}
