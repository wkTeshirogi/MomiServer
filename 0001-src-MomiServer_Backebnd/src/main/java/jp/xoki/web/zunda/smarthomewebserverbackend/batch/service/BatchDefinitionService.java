package jp.xoki.web.zunda.smarthomewebserverbackend.batch.service;

import static jp.xoki.web.zunda.smarthomewebserverbackend.batch.constants.BatchDefinitionConstants.ACTIVE_STATUS;



import jp.xoki.web.zunda.jooq.tables.records.MBatchDefinitionsRecord;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchDefinitionDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchDefinitionRegistRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository.BatchDefinitionRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository.BatchExecutionRequestRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.OptimisticLockConflictException;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.constants.BatchMessageConstants;

/** バッチ定義マスタの取得、登録、更新、DTO変換を行うService。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BatchDefinitionService {

	private final BatchDefinitionRepository batchDefinitionRepository;
	private final BatchExecutionRequestRepository batchExecutionRequestRepository;
	private final BatchExecutionControlService batchExecutionControlService;

	/** @return バッチ定義一覧 */
	public List<BatchDefinitionDetailDto> getAllBatchDefinitions() {
		return batchDefinitionRepository.findAll().stream()
				.map(this::toDetailDto)
				.toList();
	}

	/**
	 * バッチ定義詳細を取得する。
	 *
	 * @param id バッチ定義ID
	 * @return バッチ定義詳細
	 */
	public BatchDefinitionDetailDto getBatchDefinitionDetailDto(long id) {
		return batchDefinitionRepository.findById(id).map(this::toDetailDto).orElse(null);
	}

	/**
	 * バッチ定義を登録または更新する。
	 *
	 * @param request 登録更新リクエスト
	 * @return 登録更新後のバッチ定義詳細
	 */
	public BatchDefinitionDetailDto save(BatchDefinitionRegistRequest request) {
		if (request.getId() != null && request.getId() > 0) {
			MBatchDefinitionsRecord current = batchDefinitionRepository.findById(request.getId())
					.orElseThrow(() -> new ResourceNotFoundException("batch definition", request.getId()));
			validateUpdate(request, current);
			boolean becameDisabled = Boolean.TRUE.equals(current.getEnabled())
					&& Boolean.FALSE.equals(request.getEnabled());

			int updatedCount = batchDefinitionRepository.update(toRecord(request), request.getVersion());
			if (updatedCount == 0) {
				throw conflict();
			}
			if (becameDisabled) cancelUnstartedRequests(request.getId());
			return getBatchDefinitionDetailDto(request.getId());
		}

		Long savedId = batchDefinitionRepository.save(toRecord(request));
		return getBatchDefinitionDetailDto(savedId);
	}

	private BatchDefinitionDetailDto toDetailDto(MBatchDefinitionsRecord databaseRecord) {
		return BatchDefinitionDetailDto.builder()
				.id(databaseRecord.getId())
				.version(databaseRecord.getVersion())
				.status(databaseRecord.getStatus())
				.batchName(databaseRecord.getBatchName())
				.viewName(databaseRecord.getViewName())
				.description(databaseRecord.getDescription())
				.enabled(databaseRecord.getEnabled())
				.category(databaseRecord.getCategory())
				.tags(databaseRecord.getTags())
				.executionType(databaseRecord.getExecutionType())
				.executionCommand(databaseRecord.getExecutionCommand())
				.workingDirectory(databaseRecord.getWorkingDirectory())
				.timeoutSeconds(databaseRecord.getTimeoutSeconds())
				.maxRetryCount(databaseRecord.getMaxRetryCount())
				.retryIntervalSeconds(databaseRecord.getRetryIntervalSeconds())
				.executionUser(databaseRecord.getExecutionUser())
				.requiredRole(databaseRecord.getRequiredRole())
				.priority(databaseRecord.getPriority())
				.allowParallelExecution(databaseRecord.getAllowParallelExecution())
				.logRetentionDate(databaseRecord.getLogRetentionDate())
				.logPath(databaseRecord.getLogPath())
				.successNotification(databaseRecord.getSuccessNotification())
				.failureNotification(databaseRecord.getFailureNotification())
				.createdAt(databaseRecord.getCreatedDt())
				.updatedAt(databaseRecord.getUpdatedDt())
				.build();
	}

	private MBatchDefinitionsRecord toRecord(BatchDefinitionRegistRequest request) {
		MBatchDefinitionsRecord databaseRecord = new MBatchDefinitionsRecord();
		databaseRecord.setId(request.getId());
		databaseRecord.setVersion(request.getVersion());
		databaseRecord.setStatus(request.getStatus() == null ? ACTIVE_STATUS : request.getStatus());
		databaseRecord.setBatchName(request.getBatchName());
		databaseRecord.setViewName(request.getViewName());
		databaseRecord.setDescription(request.getDescription());
		databaseRecord.setEnabled(request.getEnabled());
		databaseRecord.setCategory(request.getCategory());
		databaseRecord.setTags(request.getTags());
		databaseRecord.setExecutionType(request.getExecutionType());
		databaseRecord.setExecutionCommand(request.getExecutionCommand());
		databaseRecord.setWorkingDirectory(request.getWorkingDirectory());
		databaseRecord.setTimeoutSeconds(request.getTimeoutSeconds());
		databaseRecord.setMaxRetryCount(request.getMaxRetryCount());
		databaseRecord.setRetryIntervalSeconds(request.getRetryIntervalSeconds());
		databaseRecord.setExecutionUser(request.getExecutionUser());
		databaseRecord.setRequiredRole(request.getRequiredRole());
		databaseRecord.setPriority(request.getPriority());
		databaseRecord.setAllowParallelExecution(request.getAllowParallelExecution());
		databaseRecord.setLogRetentionDate(request.getLogRetentionDate());
		databaseRecord.setLogPath(request.getLogPath());
		databaseRecord.setSuccessNotification(request.getSuccessNotification());
		databaseRecord.setFailureNotification(request.getFailureNotification());

		return databaseRecord;

	}

	/**
	 * 更新要求の状態と更新番号を検証する。
	 *
	 * @param request 更新要求
	 * @param current 現在の定義
	 */
	private void validateUpdate(BatchDefinitionRegistRequest request, MBatchDefinitionsRecord current) {
		if (request.getVersion() == null || request.getVersion() <= 0
				|| !Objects.equals(request.getVersion(), current.getVersion())) {
			throw conflict();
		}
		if (!ACTIVE_STATUS.equals(current.getStatus())) {
			throw new ResourceNotFoundException("batch definition", request.getId());
		}
		String requestedStatus = request.getStatus() == null ? ACTIVE_STATUS : request.getStatus();
		if (!ACTIVE_STATUS.equals(requestedStatus) && !"deleted".equals(requestedStatus)) {
			throw new IllegalArgumentException("Batch definition status must be active or deleted");
		}
		if ("deleted".equals(requestedStatus)) validateDeletion(request, current);
	}

	/**
	 * 論理削除の前提条件を検証する。
	 *
	 * @param request 更新要求
	 * @param current 現在の定義
	 */
	private void validateDeletion(BatchDefinitionRegistRequest request, MBatchDefinitionsRecord current) {
		if (Boolean.TRUE.equals(current.getEnabled()) || Boolean.TRUE.equals(request.getEnabled())) {
			throw new IllegalArgumentException("Batch definition must be disabled before deletion");
		}
		if (batchDefinitionRepository.hasNonTerminalExecutionRequests(request.getId())) {
			throw new IllegalArgumentException("Batch definition has non-terminal execution requests");
		}
	}

	/**
	 * 未開始の実行要求を既存の取消フローへ渡す。
	 *
	 * @param batchDefinitionId 無効化したバッチ定義ID
	 */
	private void cancelUnstartedRequests(Long batchDefinitionId) {
		for (UUID executionId : batchExecutionRequestRepository
				.findCancellableExecutionIdsByDefinition(batchDefinitionId)) {
			try {
				batchExecutionControlService.requestCancellationIfNotStarted(
						executionId, "definition-disabled");
			} catch (RuntimeException exception) {
				log.warn("Failed to request cancellation after disabling definition. definitionId={}, executionId={}",
						batchDefinitionId, executionId, exception);
			}
		}
	}

	private OptimisticLockConflictException conflict() {
		return new OptimisticLockConflictException(
				BatchMessageConstants.DEFINITION_UPDATE_CONFLICT);
	}
}
