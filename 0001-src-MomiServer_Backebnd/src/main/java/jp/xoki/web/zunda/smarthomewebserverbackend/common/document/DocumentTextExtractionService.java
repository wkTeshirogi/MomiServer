package jp.xoki.web.zunda.smarthomewebserverbackend.common.document;

import jp.xoki.web.zunda.smarthomewebserverbackend.common.file.SecureTemporaryFiles;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.CommonMessageConstants;

/** PDF埋込テキストを優先し、必要時だけOCRへ切り替える共通抽出Service。 */
@Service
public class DocumentTextExtractionService {

	private static final Logger LOGGER = LoggerFactory.getLogger(DocumentTextExtractionService.class);
	private final OcrEngine ocrEngine;
	private final DocumentExtractionProperties properties;

	public DocumentTextExtractionService(OcrEngine ocrEngine, DocumentExtractionProperties properties) {
		this.ocrEngine = ocrEngine;
		this.properties = properties;
	}

	public DocumentExtractionResult extract(Path source, String mediaType, int pageLimit) {
		ExecutorService executor = Executors.newThreadPerTaskExecutor(
				Thread.ofVirtual().name("document-extraction-", 0).factory());
		Future<DocumentExtractionResult> task = executor.submit(() -> extractInternal(source, mediaType, pageLimit));
		try {
			return task.get(properties.getDocumentTimeout().toMillis(), TimeUnit.MILLISECONDS);
		} catch (TimeoutException exception) {
			task.cancel(true);
			throw new DocumentExtractionException(CommonMessageConstants.DOCUMENT_EXTRACTION_TIMEOUT, exception);
		} catch (InterruptedException exception) {
			task.cancel(true);
			Thread.currentThread().interrupt();
			throw new DocumentExtractionException(CommonMessageConstants.DOCUMENT_EXTRACTION_INTERRUPTED, exception);
		} catch (ExecutionException exception) {
			if (exception.getCause() instanceof DocumentExtractionException extractionException) throw extractionException;
			throw new DocumentExtractionException(CommonMessageConstants.DOCUMENT_EXTRACTION_FAILED, exception.getCause());
		} finally {
			executor.shutdownNow();
		}
	}

	private DocumentExtractionResult extractInternal(Path source, String mediaType, int pageLimit) {
		if ("application/pdf".equals(mediaType)) return extractPdf(source, pageLimit);
		return new DocumentExtractionResult(limitText(ocrEngine.extract(source)), "OCR", List.of());
	}

	private DocumentExtractionResult extractPdf(Path source, int pageLimit) {
		try (PDDocument document = Loader.loadPDF(source.toFile())) {
			if (document.isEncrypted()) throw new DocumentExtractionException(CommonMessageConstants.PDF_ENCRYPTED);
			if (document.getNumberOfPages() > pageLimit) throw new DocumentExtractionException(CommonMessageConstants.PDF_PAGE_LIMIT_EXCEEDED);
			String embedded = new BoundedPdfTextStripper(properties.getMaxExtractedCharacters()).getText(document);
			if (embedded.replaceAll("\\s+", "").length() >= 20) {
				return new DocumentExtractionResult(embedded, "PDF_TEXT", List.of());
			}
			PDFRenderer renderer = new PDFRenderer(document);
			StringBuilder pages = new StringBuilder();
			for (int page = 0; page < document.getNumberOfPages(); page++) {
				if (Thread.currentThread().isInterrupted()) throw new DocumentExtractionException(CommonMessageConstants.PDF_EXTRACTION_INTERRUPTED);
				validateRenderedPixels(document, page);
				Path image = createPageImage(source);
				try {
					BufferedImage rendered = renderer.renderImageWithDPI(page, properties.getRenderedDpi(), ImageType.RGB);
					if (!ImageIO.write(rendered, "png", image.toFile())) throw new IOException("PNG writer is unavailable");
					appendWithinLimit(pages, ocrEngine.extract(image));
				} finally {
					deleteIntermediateImage(image);
				}
			}
			return new DocumentExtractionResult(pages.toString(), "OCR", List.of(CommonMessageConstants.PDF_OCR_WARNING));
		} catch (TextLimitExceededException exception) {
			throw new DocumentExtractionException(CommonMessageConstants.DOCUMENT_TEXT_LIMIT_EXCEEDED, exception);
		} catch (IOException exception) {
			throw new DocumentExtractionException(CommonMessageConstants.PDF_EXTRACTION_FAILED, exception);
		}
	}

	private Path createPageImage(Path source) throws IOException {
		Path directory = source.toAbsolutePath().getParent();
		if (directory == null) throw new IOException("Document source has no parent directory");
		return SecureTemporaryFiles.createOwnerOnlyFile(directory, ".ocr-page.png");
	}

	private void deleteIntermediateImage(Path image) {
		try {
			Files.deleteIfExists(image);
		} catch (IOException exception) {
			LOGGER.warn("OCR intermediate image deletion failed. reference={}", image.getFileName(), exception);
		}
	}

	private void validateRenderedPixels(PDDocument document, int page) {
		double width = document.getPage(page).getCropBox().getWidth() * properties.getRenderedDpi() / 72.0;
		double height = document.getPage(page).getCropBox().getHeight() * properties.getRenderedDpi() / 72.0;
		double pixels = width * height;
		if (!Double.isFinite(pixels) || width <= 0 || height <= 0 || pixels > properties.getMaxRenderedPixels()) {
			throw new DocumentExtractionException(CommonMessageConstants.PDF_IMAGE_LIMIT_EXCEEDED);
		}
	}

	private String limitText(String text) {
		if (text != null && text.length() > properties.getMaxExtractedCharacters()) {
			throw new DocumentExtractionException(CommonMessageConstants.DOCUMENT_TEXT_LIMIT_EXCEEDED);
		}
		return text == null ? "" : text;
	}

	private void appendWithinLimit(StringBuilder target, String text) {
		String value = text == null ? "" : text;
		int separator = target.isEmpty() ? 0 : 1;
		if ((long) target.length() + separator + value.length() > properties.getMaxExtractedCharacters()) {
			throw new DocumentExtractionException(CommonMessageConstants.DOCUMENT_TEXT_LIMIT_EXCEEDED);
		}
		if (separator == 1) target.append('\n');
		target.append(value);
	}

	private static final class BoundedPdfTextStripper extends PDFTextStripper {
		private final int maximumCharacters;
		private int characters;

		private BoundedPdfTextStripper(int maximumCharacters) {
			this.maximumCharacters = maximumCharacters;
			setSortByPosition(true);
		}

		@Override
		protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
			if (Thread.currentThread().isInterrupted()) throw new IOException("PDF extraction interrupted");
			characters = Math.addExact(characters, text.length());
			if (characters > maximumCharacters) throw new TextLimitExceededException();
			super.writeString(text, textPositions);
		}
	}

	private static final class TextLimitExceededException extends IOException { }
}
