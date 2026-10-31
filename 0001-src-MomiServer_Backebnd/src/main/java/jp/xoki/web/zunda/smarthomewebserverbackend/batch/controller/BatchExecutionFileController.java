package jp.xoki.web.zunda.smarthomewebserverbackend.batch.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.service.AgentAuthorization;
import jakarta.validation.Valid;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchExecutionFileDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchExecutionFileRegisterRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.service.BatchExecutionFileService;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.security.annotation.AdminOnly;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** バッチ実行に紐づくファイルの登録、一覧取得、ダウンロードを提供するController。 */
@RestController
@RequestMapping
@RequiredArgsConstructor
public class BatchExecutionFileController {
	private final AgentAuthorization agentAuthorization;

	/** バッチ実行ファイルの業務処理Service。 */
	private final BatchExecutionFileService batchExecutionFileService;

	/**
	 * 管理者向けにバッチ実行ファイル情報を登録する。
	 *
	 * @param executionId 外部参照用実行ID
	 * @param request ファイル登録内容
	 * @return 登録後のファイル詳細レスポンス
	 */
	@AdminOnly
	@PostMapping("/api/batch/executions/{executionId}/files")
	public ResponseEntity<BatchExecutionFileDetailDto> register(
			@PathVariable UUID executionId,
			@Valid @RequestBody BatchExecutionFileRegisterRequest request
	) {
		return ResponseEntity.ok(batchExecutionFileService.register(executionId, request));
	}

	/**
	 * Agent向けにバッチ実行へ紐づくファイル一覧を返す。
	 *
	 * @param executionId 外部参照用実行ID
	 * @return バッチ実行ファイル一覧レスポンス
	 */
	@GetMapping("/api/v1/agent/batch/executions/{executionId}/files")
	@PreAuthorize("hasRole('AGENT')")
	@Transactional
	public ResponseEntity<List<BatchExecutionFileDetailDto>> list(@PathVariable UUID executionId) {
		agentAuthorization.requireExecution(executionId);
		return ResponseEntity.ok(batchExecutionFileService.list(executionId));
	}

	/**
	 * Agent向けにバッチ実行ファイルをダウンロードさせる。
	 *
	 * @param executionId 外部参照用実行ID
	 * @param fileId ファイルID
	 * @return ファイルリソースレスポンス
	 */
	@GetMapping("/api/v1/agent/batch/executions/{executionId}/files/{fileId}/download")
	@PreAuthorize("hasRole('AGENT')")
	@Transactional
	public ResponseEntity<Resource> download(@PathVariable UUID executionId, @PathVariable Long fileId) {
		agentAuthorization.requireExecution(executionId);
		BatchExecutionFileDetailDto file = batchExecutionFileService.getFile(executionId, fileId);
		Resource resource = batchExecutionFileService.download(executionId, fileId);
		MediaType mediaType = file.getContentType() != null && !file.getContentType().isBlank()
				? MediaType.parseMediaType(file.getContentType())
				: MediaType.APPLICATION_OCTET_STREAM;

		return ResponseEntity.ok()
				.contentType(mediaType)
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFileName() + "\"")
				.body(resource);
	}
}
