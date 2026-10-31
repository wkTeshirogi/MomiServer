package jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.imports;

import jp.xoki.web.zunda.smarthomewebserverbackend.common.document.DocumentExtractionException;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.document.DocumentExtractionResult;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.document.DocumentTextExtractionService;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.dto.VehicleDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.dto.VehicleSaveRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.service.VehicleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.time.ApplicationClockFactory;

@ExtendWith(MockitoExtension.class)
class VehicleImportServiceTest {

	@Mock VehicleImportRepository repository;
	@Mock DocumentTextExtractionService extractionService;
	@Mock VehicleDocumentParser parser;
	@Mock VehicleService vehicleService;
	@TempDir Path tempDirectory;
	private VehicleImportStorage storage;
	private VehicleImportService service;

	@BeforeEach
	void setUp() {
		VehicleImportProperties properties = new VehicleImportProperties();
		properties.setRoot(tempDirectory.resolve("vehicle-imports").toString());
		storage = new VehicleImportStorage(properties, ApplicationClockFactory.systemClock());
		service = new VehicleImportService(repository, storage, extractionService, parser, vehicleService, properties, ApplicationClockFactory.systemClock());
	}

	@Test
	void sourceDocumentCopiesConstructorBytes() {
		byte[] bytes = {1, 2};
		VehicleImportService.SourceDocument document = new VehicleImportService.SourceDocument(bytes, "application/pdf", "document.pdf");
		VehicleImportService.SourceDocument expected = new VehicleImportService.SourceDocument(new byte[]{1, 2}, "application/pdf", "document.pdf");
		int originalHashCode = document.hashCode();

		bytes[0] = 3;

		assertThat(document.bytes()).containsExactly((byte) 1, (byte) 2);
		assertThat(document).isEqualTo(expected);
		assertThat(document.hashCode()).isEqualTo(originalHashCode);
	}

	@Test
	void sourceDocumentReturnsIndependentBytesOnEveryAccess() {
		VehicleImportService.SourceDocument document = new VehicleImportService.SourceDocument(new byte[]{1, 2}, "application/pdf", "document.pdf");
		VehicleImportService.SourceDocument expected = new VehicleImportService.SourceDocument(new byte[]{1, 2}, "application/pdf", "document.pdf");
		int originalHashCode = document.hashCode();
		byte[] returned = document.bytes();

		returned[0] = 3;

		assertThat(document.bytes()).isNotSameAs(returned).containsExactly((byte) 1, (byte) 2);
		assertThat(document.bytes()).isNotSameAs(document.bytes());
		assertThat(document).isEqualTo(expected);
		assertThat(document.hashCode()).isEqualTo(originalHashCode);
	}

	@Test
	void sourceDocumentPreservesNullAndEmptyContent() {
		VehicleImportService.SourceDocument nullContent = new VehicleImportService.SourceDocument(null, null, null);
		byte[] bytes = {};
		VehicleImportService.SourceDocument emptyContent = new VehicleImportService.SourceDocument(bytes, "application/pdf", "document.pdf");

		assertThat(nullContent.bytes()).isNull();
		assertThat(nullContent).isEqualTo(new VehicleImportService.SourceDocument(null, null, null));
		assertThat(emptyContent.bytes()).isNotSameAs(bytes).isEmpty();
		assertThat(emptyContent.bytes()).isNotSameAs(emptyContent.bytes());
	}

	@Test
	void sourceDocumentComparesArrayContentsAndMetadata() {
		VehicleImportService.SourceDocument first = new VehicleImportService.SourceDocument(new byte[]{1, 2}, "application/pdf", "document.pdf");
		VehicleImportService.SourceDocument same = new VehicleImportService.SourceDocument(new byte[]{1, 2}, "application/pdf", "document.pdf");
		VehicleImportService.SourceDocument different = new VehicleImportService.SourceDocument(new byte[]{1, 3}, "application/pdf", "document.pdf");
		VehicleImportService.SourceDocument differentMetadata = new VehicleImportService.SourceDocument(new byte[]{1, 2}, "image/png", "image.png");
		VehicleImportService.SourceDocument differentFilename = new VehicleImportService.SourceDocument(new byte[]{1, 2}, "application/pdf", "other.pdf");

		assertThat(first).isEqualTo(same).hasSameHashCodeAs(same)
				.isNotEqualTo(different).isNotEqualTo(differentMetadata).isNotEqualTo(differentFilename);
		assertThat(same).isEqualTo(first);
		assertThat(first).isNotNull().isNotEqualTo(new Object());
		assertThat(first.toString()).contains("bytes=[1, 2]", "mediaType=application/pdf", "filename=document.pdf");
	}

	@ParameterizedTest
	@MethodSource("supportedUploads")
	void detectsSupportedContentAndPreservesUploadMetadata(String filename, String declaredType,
			String expectedType) throws IOException {
		byte[] bytes = documentBytes(expectedType);
		MockMultipartFile file = new MockMultipartFile("file", filename, declaredType, bytes);
		when(extractionService.extract(any(), eq(expectedType), eq(10)))
				.thenReturn(new DocumentExtractionResult("extracted", "PDF_TEXT", List.of()));
		when(parser.parse("extracted")).thenReturn(new VehicleDocumentParser.ParsedCandidate(
				VehicleImportCandidate.builder().build(), List.of()));

		VehicleImportDetailDto result = service.upload(file, "operator@example.com");

		assertThat(result.detected_media_type()).isEqualTo(expectedType);
		assertThat(result.status()).isEqualTo(VehicleImportStatus.PENDING_REVIEW);
		ArgumentCaptor<VehicleImportRow> saved = ArgumentCaptor.forClass(VehicleImportRow.class);
		verify(repository).insert(saved.capture());
		assertThat(saved.getValue().mediaType()).isEqualTo(expectedType);
		assertThat(Files.readAllBytes(storage.resolve(saved.getValue().storageReference()))).isEqualTo(bytes);
	}

	private static Stream<Arguments> supportedUploads() {
		return Stream.of(
				Arguments.of("document.pdf", "application/pdf", "application/pdf"),
				Arguments.of("document.PDF", "APPLICATION/PDF", "application/pdf"),
				Arguments.of("image.png", "image/png", "image/png"),
				Arguments.of("image.jpg", "image/jpeg", "image/jpeg"),
				Arguments.of("image.jpeg", "application/octet-stream", "image/jpeg"),
				Arguments.of("document.pdf", null, "application/pdf"),
				Arguments.of("document.pdf", "", "application/pdf"),
				Arguments.of("document.pdf", "  ", "application/pdf"));
	}

	@ParameterizedTest
	@MethodSource("invalidUploads")
	void rejectsInvalidContentBeforeExtraction(String filename, String declaredType, byte[] bytes) {
		MockMultipartFile file = new MockMultipartFile("file", filename, declaredType, bytes);

		assertThatThrownBy(() -> service.upload(file, "operator@example.com"))
				.isInstanceOf(IllegalArgumentException.class);

		verifyNoInteractions(extractionService, parser, repository);
	}

	private static Stream<Arguments> invalidUploads() {
		byte[] pdf = "%PDF-test".getBytes(StandardCharsets.US_ASCII);
		byte[] pngSignature = {(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a};
		return Stream.of(
				Arguments.of("document.pdf", "application/pdf", "GIF89a".getBytes(StandardCharsets.US_ASCII)),
				Arguments.of("image.png", "application/pdf", pdf),
				Arguments.of("document.pdf", "image/png", pdf),
				Arguments.of("document", "application/pdf", pdf),
				Arguments.of("empty.pdf", "application/pdf", new byte[0]),
				Arguments.of("broken.png", "image/png", pngSignature));
	}

	private static byte[] documentBytes(String mediaType) throws IOException {
		if ("application/pdf".equals(mediaType)) {
			return "%PDF-test".getBytes(StandardCharsets.US_ASCII);
		}
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB),
					"image/png".equals(mediaType) ? "png" : "jpeg", output);
			return output.toByteArray();
		}
	}

	@Test
	void confirmationDeletesPhysicalSourceAndMakesItUnavailable() {
		UUID id = UUID.randomUUID();
		VehicleImportRow pending = row(id, VehicleImportStatus.PENDING_REVIEW, LocalDateTime.now(ApplicationClockFactory.systemClock()).plusHours(1));
		Path source = createSource(pending.storageReference());
		when(repository.find(id, true)).thenReturn(pending);
		when(repository.finishPending(id, VehicleImportStatus.REGISTERED, 42L)).thenReturn(1);
		when(vehicleService.create(any(VehicleSaveRequest.class), eq("operator@example.com")))
				.thenReturn(VehicleDetailDto.builder().id(42L).build());

		commit(() -> service.confirm(id, VehicleSaveRequest.builder().build(), "operator@example.com"));

		assertThat(Files.exists(source)).isFalse();
		when(repository.find(id, false)).thenReturn(withStatus(pending, VehicleImportStatus.REGISTERED));
		assertThatThrownBy(() -> service.source(id)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void cancellationDeletesPhysicalSourceAndMakesItUnavailable() {
		UUID id = UUID.randomUUID();
		VehicleImportRow pending = row(id, VehicleImportStatus.PENDING_REVIEW, LocalDateTime.now(ApplicationClockFactory.systemClock()).plusHours(1));
		Path source = createSource(pending.storageReference());
		when(repository.find(id, true)).thenReturn(pending);
		when(repository.finishPending(id, VehicleImportStatus.CANCELLED, null)).thenReturn(1);

		commit(() -> service.cancel(id));

		assertThat(Files.exists(source)).isFalse();
		when(repository.find(id, false)).thenReturn(withStatus(pending, VehicleImportStatus.CANCELLED));
		assertThatThrownBy(() -> service.source(id)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void extractionFailureDeletesPhysicalSourceAndMakesItUnavailable() {
		MockMultipartFile file = new MockMultipartFile("file", "山田太郎_東京都新宿区.pdf", "application/pdf",
				"%PDF-invalid".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
		when(extractionService.extract(any(), eq("application/pdf"), eq(10)))
				.thenThrow(new DocumentExtractionException("解析失敗"));

		assertThatThrownBy(() -> commit(() -> service.upload(file, "operator@example.com")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("解析失敗");

		ArgumentCaptor<VehicleImportRow> row = ArgumentCaptor.forClass(VehicleImportRow.class);
		verify(repository).insert(row.capture());
		assertThat(row.getValue().status()).isEqualTo(VehicleImportStatus.PARSE_FAILED);
		assertThat(row.getValue().displayFilename()).isEqualTo("uploaded-document.pdf");
		assertThat(Files.exists(storage.resolve(row.getValue().storageReference()))).isFalse();
		when(repository.find(row.getValue().id(), false)).thenReturn(row.getValue());
		UUID failedId = row.getValue().id();
		assertThatThrownBy(() -> service.source(failedId)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void expiryCleanupDeletesPhysicalSourceAndMakesItUnavailable() {
		UUID id = UUID.randomUUID();
		VehicleImportRow pending = row(id, VehicleImportStatus.PENDING_REVIEW, LocalDateTime.now(ApplicationClockFactory.systemClock()).minusMinutes(1));
		Path source = createSource(pending.storageReference());
		when(repository.findExpiredPending(any(LocalDateTime.class))).thenReturn(List.of(pending));
		when(repository.finishPending(id, VehicleImportStatus.EXPIRED, null)).thenReturn(1);
		when(repository.findTerminalWithSource()).thenReturn(List.of());
		when(repository.findAllStorageReferences()).thenReturn(Set.of(pending.storageReference()));

		commit(service::cleanupExpired);

		assertThat(Files.exists(source)).isFalse();
		when(repository.find(id, false)).thenReturn(withStatus(pending, VehicleImportStatus.EXPIRED));
		assertThatThrownBy(() -> service.source(id)).isInstanceOf(IllegalArgumentException.class);
	}

	private VehicleImportRow row(UUID id, VehicleImportStatus status, LocalDateTime expiresAt) {
		return new VehicleImportRow(id, "uploaded-document.pdf", "application/pdf", UUID.randomUUID().toString(), status,
				VehicleImportCandidate.builder().build(), "PDF_TEXT", List.of(), expiresAt, "operator@example.com", null);
	}

	private Path createSource(String reference) {
		String actualReference = storage.save("source".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		Path actual = storage.resolve(actualReference);
		Path expected = storage.resolve(reference);
		try {
			return Files.move(actual, expected);
		} catch (java.io.IOException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private VehicleImportRow withStatus(VehicleImportRow row, VehicleImportStatus status) {
		return new VehicleImportRow(row.id(), row.displayFilename(), row.mediaType(), row.storageReference(), status,
				row.candidate(), row.extractionMethod(), row.warnings(), row.expiresAt(), row.createdBy(), row.confirmedVehicleId());
	}

	private void commit(Runnable action) {
		RuntimeException failure = null;
		TransactionSynchronizationManager.initSynchronization();
		try {
			try {
				action.run();
			} catch (RuntimeException exception) {
				failure = exception;
			}
			List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
			synchronizations.forEach(TransactionSynchronization::afterCommit);
			synchronizations.forEach(synchronization -> synchronization.afterCompletion(
					TransactionSynchronization.STATUS_COMMITTED));
		} finally {
			TransactionSynchronizationManager.clearSynchronization();
		}
		if (failure != null) throw failure;
	}
}
