package jp.xoki.web.zunda.smarthomewebserverbackend.common.document;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.awt.Graphics2D;

class TesseractOcrEngineTest {

	@TempDir
	Path temporaryDirectory;

	@Test
	void returnsOnlyStandardOutput() throws Exception {
		Path command = shellScript("echo extracted; echo warning 1>&2");
		DocumentExtractionProperties properties = properties(command, Duration.ofSeconds(2));

		assertThat(new TesseractOcrEngine(properties).extract(image())).isEqualTo("extracted\n");
	}

	@Test
	void terminatesCommandAtTimeout() throws Exception {
		Path command = shellScript("exec sleep 5");
		DocumentExtractionProperties properties = properties(command, Duration.ofMillis(50));
		TesseractOcrEngine engine = new TesseractOcrEngine(properties);
		Path image = image();

		assertThatThrownBy(() -> engine.extract(image))
				.isInstanceOf(DocumentExtractionException.class)
				.hasMessageContaining("制限時間");
	}

	@Test
	void metacharactersAndSpacesRemainOneImageArgument() throws Exception {
		Path command = shellScript("printf '%s\\n' \"$@\"");
		Path image = Files.createFile(temporaryDirectory.resolve("文書 with spaces;$(echo noop)&.png"));
		String output = new TesseractOcrEngine(properties(command, Duration.ofSeconds(2))).extract(image);
		assertThat(output).isEqualTo(image.toAbsolutePath().normalize() + "\nstdout\n-l\njpn+eng\n");
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"sh", "bash", "cmd.exe", "powershell.exe", "/bin/sh", "./tesseract",
			"tools/tesseract", "tesseract -l eng", "/"})
	void rejectsInterpretersCommandLinesAndRelativeExecutablePaths(String command) throws Exception {
		DocumentExtractionProperties properties = new DocumentExtractionProperties();
		properties.setTesseractCommand(command);
		Path image = image();
		TesseractOcrEngine engine = new TesseractOcrEngine(properties);
		assertThatThrownBy(() -> engine.extract(image))
				.isInstanceOf(DocumentExtractionException.class).hasMessageContaining("Tesseract");
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"-l eng", "eng --psm 0", "../eng", "jpn+", "+jpn", "jpn++eng",
			"jpn\neng", "eng;echo injected", "日本語", "eng_é", "jpn+ｅｎｇ"})
	void rejectsLanguageOptionsBeforeLaunchingProcess(String language) throws Exception {
		Path command = shellScript("touch \"$1.invoked\"");
		Path image = image();
		DocumentExtractionProperties properties = properties(command, Duration.ofSeconds(2));
		properties.setLanguage(language);
		TesseractOcrEngine engine = new TesseractOcrEngine(properties);
		assertThatThrownBy(() -> engine.extract(image))
				.isInstanceOf(DocumentExtractionException.class).hasMessageContaining("言語");
		assertThat(Path.of(image + ".invoked")).doesNotExist();
	}

	@Test
	void supportsScriptLanguageNamesWithoutAcceptingArbitraryPaths() throws Exception {
		Path command = shellScript("printf '%s\\n' \"$4\"");
		DocumentExtractionProperties properties = properties(command, Duration.ofSeconds(2));
		properties.setLanguage("script/Latin+jpn_vert");
		assertThat(new TesseractOcrEngine(properties).extract(image())).isEqualTo("script/Latin+jpn_vert\n");
	}

	@Test
	void rejectsNonzeroExitAndOversizedOutput() throws Exception {
		Path image = image();
		Path failed = shellScript("echo failed 1>&2; exit 2");
		TesseractOcrEngine failedEngine = new TesseractOcrEngine(properties(failed, Duration.ofSeconds(2)));
		assertThatThrownBy(() -> failedEngine.extract(image))
				.isInstanceOf(DocumentExtractionException.class).hasMessageContaining("OCRに失敗");
		Path excessive = shellScript("head -c 1048577 /dev/zero");
		TesseractOcrEngine excessiveEngine = new TesseractOcrEngine(properties(excessive, Duration.ofSeconds(2)));
		assertThatThrownBy(() -> excessiveEngine.extract(image))
				.isInstanceOf(DocumentExtractionException.class).hasMessageContaining("OCRに失敗");
	}

	@Test
	void interruptionIsRestoredAfterProcessCleanup() throws Exception {
		Path command = shellScript("exec sleep 5");
		Path image = image();
		TesseractOcrEngine engine = new TesseractOcrEngine(properties(command, Duration.ofSeconds(2)));
		Thread.currentThread().interrupt();
		try {
			assertThatThrownBy(() -> engine.extract(image))
					.isInstanceOf(DocumentExtractionException.class).hasMessageContaining("中断");
			assertThat(Thread.currentThread().isInterrupted()).isTrue();
		} finally {
			Thread.interrupted();
		}
	}

	@Test
	void rejectsMissingInputBeforeStartingTheDefaultCommand() {
		Path missing = temporaryDirectory.resolve("missing.png");
		TesseractOcrEngine engine = new TesseractOcrEngine(new DocumentExtractionProperties());
		assertThatThrownBy(() -> engine.extract(missing))
				.isInstanceOf(DocumentExtractionException.class).hasMessageContaining("入力画像");
	}

	@Test
	void rejectsSymbolicLinkInput() throws Exception {
		Assumptions.assumeFalse(System.getProperty("os.name").toLowerCase().contains("windows"));
		Path image = image();
		Path link = Files.createSymbolicLink(temporaryDirectory.resolve("link.png"), image);
		TesseractOcrEngine engine = new TesseractOcrEngine(new DocumentExtractionProperties());
		assertThatThrownBy(() -> engine.extract(link))
				.isInstanceOf(DocumentExtractionException.class).hasMessageContaining("入力画像");
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "TESSERACT_INTEGRATION_TEST", matches = "true")
	void installedTesseractReadsAnOrdinaryImage() throws Exception {
		BufferedImage bitmap = new BufferedImage(800, 140, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = bitmap.createGraphics();
		try {
			graphics.setColor(Color.WHITE);
			graphics.fillRect(0, 0, bitmap.getWidth(), bitmap.getHeight());
			graphics.setColor(Color.BLACK);
			graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, 48));
			graphics.drawString("OCR TEST 1234", 30, 85);
		} finally {
			graphics.dispose();
		}
		Path image = temporaryDirectory.resolve("ordinary scan.png");
		assertThat(ImageIO.write(bitmap, "png", image.toFile())).isTrue();
		DocumentExtractionProperties properties = new DocumentExtractionProperties();
		properties.setTesseractCommand(System.getenv().getOrDefault("TESSERACT_COMMAND", "tesseract"));
		assertThat(new TesseractOcrEngine(properties).extract(image)).contains("OCR", "1234");
	}

	private Path image() throws Exception {
		return Files.createTempFile(temporaryDirectory, "image-", ".png");
	}

	private Path shellScript(String body) throws Exception {
		Assumptions.assumeFalse(System.getProperty("os.name").toLowerCase().contains("windows"));
		Path directory = Files.createDirectory(temporaryDirectory.resolve(UUIDHolder.next()));
		Path script = directory.resolve("tesseract");
		Files.writeString(script, "#!/bin/sh\n" + body + "\n");
		Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));
		return script;
	}

	private DocumentExtractionProperties properties(Path command, Duration timeout) {
		DocumentExtractionProperties properties = new DocumentExtractionProperties();
		properties.setTesseractCommand(command.toString());
		properties.setTimeout(timeout);
		return properties;
	}

	private static final class UUIDHolder {
		private static String next() { return java.util.UUID.randomUUID().toString(); }
	}
}
