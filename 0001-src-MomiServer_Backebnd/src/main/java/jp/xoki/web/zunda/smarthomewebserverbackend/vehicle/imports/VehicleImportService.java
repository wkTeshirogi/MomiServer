package jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.imports;

import jp.xoki.web.zunda.smarthomewebserverbackend.common.document.DocumentExtractionException;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.document.DocumentExtractionResult;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.document.DocumentTextExtractionService;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.ResourceNotFoundException;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.dto.VehicleDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.dto.VehicleSaveRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.service.VehicleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Arrays;
import java.util.Objects;
import java.util.Iterator;
import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_PDF_VALUE;
import static org.springframework.http.MediaType.IMAGE_PNG_VALUE;
import static org.springframework.http.MediaType.IMAGE_JPEG_VALUE;
import java.time.Clock;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.constants.VehicleMessageConstants;

/** 車両取込の受付、候補確認、原本削除を管理するService。 */
@Service
public class VehicleImportService {
	private final VehicleImportRepository repository;
	private final VehicleImportStorage storage;
	private final DocumentTextExtractionService extractionService;
	private final VehicleDocumentParser parser;
	private final VehicleService vehicleService;
	private final VehicleImportProperties properties;

	private final Clock clock;

	public VehicleImportService(VehicleImportRepository repository, VehicleImportStorage storage,
			DocumentTextExtractionService extractionService, VehicleDocumentParser parser, VehicleService vehicleService,
			VehicleImportProperties properties, Clock clock) {
		this.repository = repository; this.storage = storage; this.extractionService = extractionService; this.parser = parser;
		this.vehicleService = vehicleService; this.properties = properties;
		this.clock = clock;
	}

	@Transactional(noRollbackFor = VehicleImportParseException.class)
	public VehicleImportDetailDto upload(MultipartFile file, String operator) {
		byte[] bytes = readAndValidateSize(file);
		String mediaType = detectMediaType(bytes, file.getOriginalFilename(), file.getContentType());
		validateImage(bytes, mediaType);
		String reference = storage.save(bytes);
		storage.deleteAfterRollback(reference);
		UUID id = UUID.randomUUID();
		LocalDateTime expiresAt = LocalDateTime.now(clock).plus(properties.getReviewLifetime());
		try {
			DocumentExtractionResult extraction = extractionService.extract(storage.resolve(reference), mediaType, properties.getMaxPdfPages());
			VehicleDocumentParser.ParsedCandidate parsed = parser.parse(extraction.text());
			VehicleImportRow row = new VehicleImportRow(id, displayFilename(mediaType), mediaType, reference,
					VehicleImportStatus.PENDING_REVIEW, parsed.candidate(), extraction.method(), combine(extraction.warnings(), parsed.warnings()),
					expiresAt, operator, null);
			repository.insert(row);
			return detail(row);
		} catch (DocumentExtractionException exception) {
			VehicleImportRow failed = new VehicleImportRow(id, displayFilename(mediaType), mediaType, reference,
					VehicleImportStatus.PARSE_FAILED, VehicleImportCandidate.builder().build(), null, List.of(), expiresAt, operator, null);
			repository.insert(failed);
			storage.deleteAfterCommit(reference);
			throw new VehicleImportParseException(exception.getMessage());
		}
	}

	@Transactional(noRollbackFor = VehicleImportExpiredException.class)
	public VehicleImportDetailDto get(UUID id) { return detail(requireReviewable(id, false)); }

	@Transactional(noRollbackFor = VehicleImportExpiredException.class)
	public SourceDocument source(UUID id) throws IOException {
		VehicleImportRow row = requireReviewable(id, false);
		Path path = storage.resolve(row.storageReference());
		return new SourceDocument(Files.readAllBytes(path), row.mediaType(), row.displayFilename());
	}

	@Transactional(noRollbackFor = VehicleImportExpiredException.class)
	public VehicleDetailDto confirm(UUID id, VehicleSaveRequest request, String operator) {
		VehicleImportRow row = requireReviewable(id, true);
		VehicleDetailDto vehicle = vehicleService.create(request, operator);
		finishPending(row.id(), VehicleImportStatus.REGISTERED, vehicle.getId());
		storage.deleteAfterCommit(row.storageReference());
		return vehicle;
	}

	@Transactional(noRollbackFor = VehicleImportExpiredException.class)
	public void cancel(UUID id) {
		VehicleImportRow row = requireReviewable(id, true);
		finishPending(row.id(), VehicleImportStatus.CANCELLED, null);
		storage.deleteAfterCommit(row.storageReference());
	}

	/** 期限切れ取込と不要な原本の整理。
	 *
	 * Schedulerからプロキシ経由で呼び出し、DB更新後のコミットに合わせて原本削除を行う。
	 */
	@Transactional
	public void cleanupExpired() {
		for (VehicleImportRow row : repository.findExpiredPending(LocalDateTime.now(clock))) {
			finishPending(row.id(), VehicleImportStatus.EXPIRED, null);
			storage.deleteAfterCommit(row.storageReference());
		}
		for (VehicleImportRow row : repository.findTerminalWithSource()) storage.deleteAfterCommit(row.storageReference());
		storage.deleteOrphans(repository.findAllStorageReferences(), LocalDateTime.now(clock).minus(properties.getReviewLifetime()));
	}

	private VehicleImportRow requireReviewable(UUID id, boolean lock) {
		VehicleImportRow row = repository.find(id, lock);
		if (row == null) throw new ResourceNotFoundException("vehicle import", id);
		if (row.status() == VehicleImportStatus.PENDING_REVIEW && !row.expiresAt().isAfter(LocalDateTime.now(clock))) {
			finishPending(row.id(), VehicleImportStatus.EXPIRED, null);
			storage.deleteAfterCommit(row.storageReference());
			throw new VehicleImportExpiredException(VehicleMessageConstants.IMPORT_NOT_REVIEWABLE);
		}
		if (row.status() != VehicleImportStatus.PENDING_REVIEW)
			throw new IllegalArgumentException(VehicleMessageConstants.IMPORT_NOT_REVIEWABLE);
		return row;
	}

	private void finishPending(UUID id, VehicleImportStatus status, Long vehicleId) {
		if (repository.finishPending(id, status, vehicleId) != 1) {
			throw new IllegalArgumentException(VehicleMessageConstants.IMPORT_STATE_CONFLICT);
		}
	}

	private byte[] readAndValidateSize(MultipartFile file) {
		if (file == null || file.isEmpty()) throw new IllegalArgumentException(VehicleMessageConstants.IMPORT_FILE_EMPTY);
		if (file.getSize() > properties.getMaxBytes()) throw new IllegalArgumentException(VehicleMessageConstants.IMPORT_SIZE_EXCEEDED);
		try (InputStream input = file.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			input.transferTo(new java.io.FilterOutputStream(output) {
				long count;
				@Override public void write(byte[] bytes, int offset, int length) throws IOException {
					count += length;
					if (count > properties.getMaxBytes()) {
						throw new IOException("too large");
					}
					super.write(bytes, offset, length);
				}
			});
			return output.toByteArray();
		} catch (IOException _) { throw new IllegalArgumentException(VehicleMessageConstants.IMPORT_SIZE_EXCEEDED); }
	}

	private String detectMediaType(byte[] bytes, String filename, String declaredType) {
		String detected = detectContentType(bytes);
		String extension = filename == null ? "" : filename.toLowerCase().replaceAll("^.*\\.", "");
		if (!extensionMatches(extension, detected)) {
			throw new IllegalArgumentException(VehicleMessageConstants.IMPORT_FILENAME_TYPE_MISMATCH);
		}
		if (declaredType != null && !declaredType.isBlank() && !"application/octet-stream".equalsIgnoreCase(declaredType)
				&& !declaredType.equalsIgnoreCase(detected)) throw new IllegalArgumentException(VehicleMessageConstants.IMPORT_MEDIA_TYPE_MISMATCH);
		return detected;
	}

	private String detectContentType(byte[] bytes) {
		if (startsWith(bytes, new byte[] {'%', 'P', 'D', 'F', '-'})) {
			return APPLICATION_PDF_VALUE;
		}
		if (startsWith(bytes, new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a})) {
			return IMAGE_PNG_VALUE;
		}
		if (startsWith(bytes, new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff})) {
			return IMAGE_JPEG_VALUE;
		}
		throw new IllegalArgumentException(VehicleMessageConstants.IMPORT_MEDIA_TYPE_UNSUPPORTED);
	}

	private boolean extensionMatches(String extension, String mediaType) {
		return switch (mediaType) {
			case APPLICATION_PDF_VALUE -> "pdf".equals(extension);
			case IMAGE_PNG_VALUE -> "png".equals(extension);
			case IMAGE_JPEG_VALUE -> "jpg".equals(extension) || "jpeg".equals(extension);
			default -> false;
		};
	}

	private void validateImage(byte[] bytes, String mediaType) {
		if (APPLICATION_PDF_VALUE.equals(mediaType)) return;
		try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
			if (input == null) throw new IllegalArgumentException(VehicleMessageConstants.IMPORT_IMAGE_INVALID);
			Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext()) {
				throw new IllegalArgumentException(VehicleMessageConstants.IMPORT_IMAGE_INVALID);
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(input, true, true);
				long pixels = Math.multiplyExact((long) reader.getWidth(0), (long) reader.getHeight(0));
				if (pixels <= 0 || pixels > properties.getMaxImagePixels()) {
					throw new IllegalArgumentException(VehicleMessageConstants.IMPORT_IMAGE_SIZE_EXCEEDED);
				}
			} finally {
				reader.dispose();
			}
		} catch (IOException _) {
			throw new IllegalArgumentException(VehicleMessageConstants.IMPORT_IMAGE_INVALID);
		} catch (ArithmeticException _) {
			throw new IllegalArgumentException(VehicleMessageConstants.IMPORT_IMAGE_SIZE_EXCEEDED);
		}
	}

	private boolean startsWith(byte[] value, byte[] signature) {
		if (value.length < signature.length) return false;
		for (int index = 0; index < signature.length; index++) if (value[index] != signature[index]) return false;
		return true;
	}

	private String displayFilename(String mediaType) {
		return switch (mediaType) {
			case APPLICATION_PDF_VALUE -> "uploaded-document.pdf";
			case IMAGE_PNG_VALUE -> "uploaded-document.png";
			default -> "uploaded-document.jpg";
		};
	}

	private List<String> combine(List<String> first, List<String> second) {
		return java.util.stream.Stream.concat(first.stream(), second.stream()).toList();
	}

	private VehicleImportDetailDto detail(VehicleImportRow row) {
		return VehicleImportDetailDto.builder().id(row.id()).status(row.status()).display_filename(row.displayFilename())
				.detected_media_type(row.mediaType())
				.extraction_method(row.extractionMethod()).candidate(row.candidate()).warnings(row.warnings()).expires_at(row.expiresAt())
				.confirmed_vehicle_id(row.confirmedVehicleId()).build();
	}

	/** 確認中だけControllerへ渡す原本バイト列。 */
	public record SourceDocument(byte[] bytes, String mediaType, String filename) {
		public SourceDocument {
			bytes = bytes == null ? null : bytes.clone();
		}

		@Override
		public byte[] bytes() {
			return bytes == null ? null : bytes.clone();
		}

		@Override
		public boolean equals(Object other) {
			if (this == other) {
				return true;
			}
			return other instanceof SourceDocument(byte[] documentBytes, String documentMediaType, String documentFilename)
					&& Arrays.equals(bytes, documentBytes)
					&& Objects.equals(mediaType, documentMediaType)
					&& Objects.equals(filename, documentFilename);
		}

		@Override
		public int hashCode() {
			return Objects.hash(Arrays.hashCode(bytes), mediaType, filename);
		}

		@Override
		public String toString() {
			return "SourceDocument[bytes=" + Arrays.toString(bytes)
					+ ", mediaType=" + mediaType + ", filename=" + filename + "]";
		}
	}

	private static final class VehicleImportParseException extends IllegalArgumentException {
		private VehicleImportParseException(String message) { super(message); }
	}

	private static final class VehicleImportExpiredException extends IllegalArgumentException {
		private VehicleImportExpiredException(String message) { super(message); }
	}
}
