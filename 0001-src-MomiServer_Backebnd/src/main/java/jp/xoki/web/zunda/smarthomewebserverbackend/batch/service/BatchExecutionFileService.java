package jp.xoki.web.zunda.smarthomewebserverbackend.batch.service;

import static jp.xoki.web.zunda.smarthomewebserverbackend.batch.constants.BatchExecutionFileServiceConstants.RESOURCE_NAME;


import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchExecutionFileDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchExecutionFileRegisterRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchExecutionRequestDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository.BatchExecutionFileRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository.BatchExecutionRequestRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** バッチ実行ファイルの登録、検証、ダウンロードを扱うService。 */
@Service
@RequiredArgsConstructor
public class BatchExecutionFileService {

	/** バッチ実行要求Repository。 */
	private final BatchExecutionRequestRepository batchExecutionRequestRepository;

	/** バッチ実行ファイルRepository。 */
	private final BatchExecutionFileRepository batchExecutionFileRepository;

	/**
	 * バッチ実行に紐づくファイル情報を登録する。
	 *
	 * @param executionId 外部参照用実行ID
	 * @param request ファイル登録内容
	 * @return 登録後のファイル詳細
	 */
	public BatchExecutionFileDetailDto register(UUID executionId, BatchExecutionFileRegisterRequest request) {
		BatchExecutionRequestDetailDto execution = getExecution(executionId);
		Path path = Path.of(request.getFilePath()).normalize();

		// 実在する通常ファイルのみ登録対象にする。
		if (!Files.isRegularFile(path)) {
			throw new ResourceNotFoundException(RESOURCE_NAME, request.getFilePath());
		}

		try {
			BatchExecutionFileDetailDto file = BatchExecutionFileDetailDto.builder()
					.executionRequestId(execution.getId())
					.fileName(resolveFileName(path, request.getFileName()))
					.filePath(path.toAbsolutePath().toString())
					.contentType(request.getContentType())
					.fileSize(Files.size(path))
					.sha256(sha256(path))
					.build();
			Long id = batchExecutionFileRepository.create(file);
			return batchExecutionFileRepository.findById(id)
					.orElseThrow(() -> new ResourceNotFoundException(RESOURCE_NAME, id));
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to register batch execution file", exception);
		}
	}

	/**
	 * バッチ実行に紐づくファイル一覧を取得する。
	 *
	 * @param executionId 外部参照用実行ID
	 * @return ファイル詳細一覧
	 */
	public List<BatchExecutionFileDetailDto> list(UUID executionId) {
		BatchExecutionRequestDetailDto execution = getExecution(executionId);
		return batchExecutionFileRepository.findByExecutionRequestId(execution.getId());
	}

	/**
	 * ダウンロード対象ファイルをResourceとして取得する。
	 *
	 * @param executionId 外部参照用実行ID
	 * @param fileId ファイルID
	 * @return ファイルResource
	 */
	public Resource download(UUID executionId, Long fileId) {
		BatchExecutionRequestDetailDto execution = getExecution(executionId);
		BatchExecutionFileDetailDto file = batchExecutionFileRepository.findById(fileId)
				.filter(item -> execution.getId().equals(item.getExecutionRequestId()))
				.orElseThrow(() -> new ResourceNotFoundException(RESOURCE_NAME, fileId));

		Path path = Path.of(file.getFilePath()).normalize();

		// 登録後にファイルが削除された場合はダウンロード不可として扱う。
		if (!Files.isRegularFile(path)) {
			throw new ResourceNotFoundException(RESOURCE_NAME, fileId);
		}
		return new FileSystemResource(path);
	}

	/**
	 * バッチ実行に紐づくファイル詳細を取得する。
	 *
	 * @param executionId 外部参照用実行ID
	 * @param fileId ファイルID
	 * @return ファイル詳細
	 */
	public BatchExecutionFileDetailDto getFile(UUID executionId, Long fileId) {
		BatchExecutionRequestDetailDto execution = getExecution(executionId);
		return batchExecutionFileRepository.findById(fileId)
				.filter(item -> execution.getId().equals(item.getExecutionRequestId()))
				.orElseThrow(() -> new ResourceNotFoundException(RESOURCE_NAME, fileId));
	}

	/**
	 * 外部参照用実行IDに一致するバッチ実行要求を取得する。
	 *
	 * @param executionId 外部参照用実行ID
	 * @return バッチ実行要求詳細
	 */
	private BatchExecutionRequestDetailDto getExecution(UUID executionId) {
		return batchExecutionRequestRepository.findByExecutionId(executionId)
				.orElseThrow(() -> new ResourceNotFoundException("batch execution request", executionId));
	}

	/**
	 * リクエスト指定名があればそれを使い、未指定時はパスからファイル名を決定する。
	 *
	 * @param path ファイルパス
	 * @param fileName リクエスト指定ファイル名
	 * @return 登録に使用するファイル名
	 */
	private String resolveFileName(Path path, String fileName) {

		// 表示名が指定されている場合は物理ファイル名より優先する。
		if (fileName != null && !fileName.isBlank()) {
			return fileName;
		}
		Path name = path.getFileName();
		if (name == null) {
			throw new IllegalArgumentException("File path has no file name");
		}
		return name.toString();
	}

	/**
	 * ファイル内容のSHA-256ハッシュを計算する。
	 *
	 * @param path 対象ファイルパス
	 * @return SHA-256ハッシュ文字列
	 * @throws IOException ファイル読み込みに失敗した場合
	 */
	private String sha256(Path path) throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (InputStream inputStream = Files.newInputStream(path)) {
				byte[] buffer = new byte[8192];
				int read;
				while ((read = inputStream.read(buffer)) >= 0) {
					digest.update(buffer, 0, read);
				}
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available", exception);
		}
	}
}
