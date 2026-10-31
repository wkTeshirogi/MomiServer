package jp.xoki.web.zunda.smarthomewebserverbackend.common.document;

import org.junit.jupiter.api.Test;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.concurrent.atomic.AtomicReference;

class DocumentTextExtractionServiceTest {
	@Test
	void scannedPdfWithAFilenameOnlyUsesItsAbsoluteParentAndDeletesIntermediateImage() throws Exception {
		Path pdf = Files.createTempFile(Path.of("").toAbsolutePath(), "ocr-relative-", ".pdf");
		AtomicReference<Path> intermediate = new java.util.concurrent.atomic.AtomicReference<Path>();
		DocumentExtractionProperties properties = new DocumentExtractionProperties();
		properties.setRenderedDpi(36);
		DocumentTextExtractionService service = new DocumentTextExtractionService(image -> {
			assertThat(image).isAbsolute();
			intermediate.set(image);
			return "recognized text";
		}, properties);
		try {
			try (PDDocument document = new PDDocument()) {
				document.addPage(new PDPage());
				document.save(pdf.toFile());
			}
			Path filenameOnly = Path.of("").toAbsolutePath().relativize(pdf);
			DocumentExtractionResult result = service.extract(filenameOnly, "application/pdf", 1);
			assertThat(result.method()).isEqualTo("OCR");
			assertThat(result.text()).isEqualTo("recognized text");
			assertThat(intermediate.get()).isNotNull().doesNotExist();
		} finally {
			Files.deleteIfExists(pdf);
		}
	}

	@Test
	void extractsPdfInVisualOrderInsteadOfContentStreamOrder() throws Exception {
		Path pdf = Files.createTempFile("vehicle-import-order-", ".pdf");
		try {
			try (PDDocument document = new PDDocument()) {
				PDPage page = new PDPage();
				document.addPage(page);
				try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
					for (int y : new int[]{600, 720}) {
						stream.beginText();
						stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
						stream.newLineAtOffset(72, (float) y);
						stream.showText(y == 720 ? "Header appears visually first" : "Value appears visually second");
						stream.endText();
					}
				}
				document.save(pdf.toFile());
			}
			DocumentTextExtractionService service = new DocumentTextExtractionService(image -> { throw new AssertionError("OCR should not be used"); }, new DocumentExtractionProperties());
			DocumentExtractionResult result = service.extract(pdf, "application/pdf", 10);
			assertThat(result.method()).isEqualTo("PDF_TEXT");
			assertThat(result.text().indexOf("Header")).isLessThan(result.text().indexOf("Value"));
		} finally {
			Files.deleteIfExists(pdf);
		}
	}

	@Test
	void usesInjectedOcrForImageWithoutRequiringTesseract() throws Exception {
		DocumentExtractionProperties properties = new DocumentExtractionProperties();
		OcrEngine ocr = image -> "車台番号: TEST-001";
		DocumentTextExtractionService service = new DocumentTextExtractionService(ocr, properties);
		Path image = Files.createTempFile("vehicle-import-test-", ".png");
		try {
			DocumentExtractionResult result = service.extract(image, "image/png", 10);
			assertThat(result.method()).isEqualTo("OCR");
			assertThat(result.text()).isEqualTo("車台番号: TEST-001");
		} finally {
			Files.deleteIfExists(image);
		}
	}

	@Test
	void rejectsPdfPageThatWouldExceedRenderedPixelLimit() throws Exception {
		DocumentExtractionProperties properties = new DocumentExtractionProperties();
		properties.setMaxRenderedPixels(100);
		DocumentTextExtractionService service = new DocumentTextExtractionService(image -> "unused", properties);
		Path pdf = Files.createTempFile("vehicle-import-test-", ".pdf");
		try (PDDocument document = new PDDocument()) {
			document.addPage(new PDPage(new PDRectangle(1_000, 1_000)));
			document.save(pdf.toFile());
		}
		try {
			assertThatThrownBy(() -> service.extract(pdf, "application/pdf", 10))
					.isInstanceOf(DocumentExtractionException.class)
					.hasMessageContaining("画像サイズ");
		} finally {
			Files.deleteIfExists(pdf);
		}
	}

	@Test
	void boundsPdfTextWhileItIsBeingExtracted() throws Exception {
		DocumentExtractionProperties properties = new DocumentExtractionProperties();
		properties.setMaxExtractedCharacters(10);
		DocumentTextExtractionService service = new DocumentTextExtractionService(image -> "unused", properties);
		Path pdf = textPdf("This text is longer than ten characters");
		try {
			assertThatThrownBy(() -> service.extract(pdf, "application/pdf", 10))
					.isInstanceOf(DocumentExtractionException.class)
					.hasMessageContaining("抽出文字数");
		} finally {
			Files.deleteIfExists(pdf);
		}
	}

	@Test
	void boundsWholeDocumentProcessingTime() throws Exception {
		DocumentExtractionProperties properties = new DocumentExtractionProperties();
		properties.setDocumentTimeout(Duration.ofMillis(50));
		CountDownLatch releaseOcr = new CountDownLatch(1);
		CountDownLatch ocrInterrupted = new CountDownLatch(1);
		OcrEngine slowOcr = image -> {
			try { releaseOcr.await(); }
			catch (InterruptedException _) {
				Thread.currentThread().interrupt();
				ocrInterrupted.countDown();
			}
			return "late";
		};
		DocumentTextExtractionService service = new DocumentTextExtractionService(slowOcr, properties);
		Path image = Files.createTempFile("vehicle-import-timeout-", ".png");
		try {
			assertThatThrownBy(() -> service.extract(image, "image/png", 10))
					.isInstanceOf(DocumentExtractionException.class)
					.hasMessageContaining("制限時間");
			assertThat(ocrInterrupted.await(5, TimeUnit.SECONDS)).isTrue();
		} finally {
			releaseOcr.countDown();
			Files.deleteIfExists(image);
		}
	}

	private Path textPdf(String text) throws Exception {
		Path pdf = Files.createTempFile("vehicle-import-text-", ".pdf");
		try (PDDocument document = new PDDocument()) {
			PDPage page = new PDPage();
			document.addPage(page);
			try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
				stream.beginText();
				stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
				stream.newLineAtOffset(72, 720);
				stream.showText(text);
				stream.endText();
			}
			document.save(pdf.toFile());
		}
		return pdf;
	}
}
