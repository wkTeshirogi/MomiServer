package jp.xoki.web.zunda.smarthomewebserverbackend.batch.service;

import jp.xoki.web.zunda.jooq.tables.records.TBatchExecutionHistoriesRecord;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchExecutionHistoryDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchExecutionHistoryRegistRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository.BatchExecutionHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.ResourceNotFoundException;

import java.util.List;

/** バッチ実行履歴の取得、登録、更新、DTO変換を行うService。 */
@Service
@RequiredArgsConstructor
public class BatchExecutionHistoryService {
	private final BatchExecutionHistoryRepository batchExecutionHistoryRepository;

	/**
	 * バッチ定義IDに紐づく実行履歴一覧を取得する。
	 *
	 * @param batchDefinitionId バッチ定義ID
	 * @return バッチ実行履歴一覧
	 */
	public List<BatchExecutionHistoryDetailDto> getBatchExecutionHistories(Long batchDefinitionId) {
		return batchExecutionHistoryRepository.findByBatchDefinitionId(batchDefinitionId).stream()
				.map(this::toDetailDto)
				.toList();
	}

	/**
	 * バッチ実行履歴詳細を取得する。
	 *
	 * @param id バッチ実行履歴ID
	 * @return バッチ実行履歴詳細
	 */
	public BatchExecutionHistoryDetailDto getBatchExecutionHistoryDetailDto(long id) {
		return batchExecutionHistoryRepository.findById(id).map(this::toDetailDto).orElse(null);
	}

	/**
	 * バッチ実行履歴を登録または更新する。
	 *
	 * @param request 登録更新リクエスト
	 * @return 登録更新後のバッチ実行履歴詳細
	 */
	public BatchExecutionHistoryDetailDto save(BatchExecutionHistoryRegistRequest request) {
		if (request.getId() != null && request.getId() > 0) {
			int updatedCount = batchExecutionHistoryRepository.update(toRecord(request));
			if (updatedCount == 0) {
				throw new ResourceNotFoundException("batch execution history", request.getId());
			}
			return getBatchExecutionHistoryDetailDto(request.getId());
		}

		Long savedId = batchExecutionHistoryRepository.save(toRecord(request));
		return getBatchExecutionHistoryDetailDto(savedId);
	}

	private BatchExecutionHistoryDetailDto toDetailDto(TBatchExecutionHistoriesRecord databaseRecord) {
		return BatchExecutionHistoryDetailDto.builder()
				.id(databaseRecord.getId())
				.batchDefinitionId(databaseRecord.getBatchDefinitionId())
				.testExecution(databaseRecord.getTestExecution())
				.executionStartedDt(databaseRecord.getExecutionStartedDt())
				.executionFinishedDt(databaseRecord.getExecutionFinishedDt())
				.executionResult(databaseRecord.getExecutionResult())
				.exitCode(databaseRecord.getExitCode())
				.logPath(databaseRecord.getLogPath())
				.message(databaseRecord.getMessage())
				.createdAt(databaseRecord.getCreatedDt())
				.updatedAt(databaseRecord.getUpdatedDt())
				.build();
	}

	private TBatchExecutionHistoriesRecord toRecord(BatchExecutionHistoryRegistRequest request) {
		TBatchExecutionHistoriesRecord databaseRecord = new TBatchExecutionHistoriesRecord();
		databaseRecord.setId(request.getId());
		databaseRecord.setBatchDefinitionId(request.getBatchDefinitionId());
		databaseRecord.setTestExecution(request.getTestExecution());
		databaseRecord.setExecutionStartedDt(request.getExecutionStartedDt());
		databaseRecord.setExecutionFinishedDt(request.getExecutionFinishedDt());
		databaseRecord.setExecutionResult(request.getExecutionResult());
		databaseRecord.setExitCode(request.getExitCode());
		databaseRecord.setLogPath(request.getLogPath());
		databaseRecord.setMessage(request.getMessage());
		return databaseRecord;
	}
}
