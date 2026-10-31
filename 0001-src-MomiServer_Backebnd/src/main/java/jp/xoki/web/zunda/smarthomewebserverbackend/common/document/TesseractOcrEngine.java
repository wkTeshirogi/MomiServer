package jp.xoki.web.zunda.smarthomewebserverbackend.common.document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.TesseractOcrEngineConstants.MAX_OUTPUT_BYTES;
import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.TesseractOcrEngineConstants.EXECUTABLE_NAMES;
import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.TesseractOcrEngineConstants.LANGUAGE_NAME;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.CommonMessageConstants;

/** ローカルTesseract CLIをシェルを介さず実行するOCR実装。 */
@Component
public class TesseractOcrEngine implements OcrEngine {

	private final DocumentExtractionProperties properties;

	public TesseractOcrEngine(DocumentExtractionProperties properties) {
		this.properties = properties;
	}

	@Override
	public String extract(Path image) {
		Process process;
		try {
			process = startTesseract(image);
		} catch (IOException | InvalidPathException exception) {
			throw new DocumentExtractionException(CommonMessageConstants.OCR_UNAVAILABLE, exception);
		}
		try (ExecutorService outputReader = Executors.newFixedThreadPool(2)) {
			return readOutput(process, outputReader);
		}
	}

	/** 出力を読み取り、Executorを閉じる前にプロセス停止と読取り中断を行う。 */
	private String readOutput(Process process, ExecutorService outputReader) {
		try {
			Future<byte[]> output = outputReader.submit(() -> process.getInputStream().readNBytes(MAX_OUTPUT_BYTES + 1));
			Future<byte[]> error = outputReader.submit(() -> process.getErrorStream().readNBytes(MAX_OUTPUT_BYTES + 1));
			boolean completed = process.waitFor(properties.getTimeout().toMillis(), TimeUnit.MILLISECONDS);
			if (!completed) {
				terminate(process);
				throw new DocumentExtractionException(CommonMessageConstants.OCR_TIMEOUT);
			}
			byte[] captured = output.get(5, TimeUnit.SECONDS);
			byte[] capturedError = error.get(5, TimeUnit.SECONDS);
			if (captured.length > MAX_OUTPUT_BYTES || capturedError.length > MAX_OUTPUT_BYTES || process.exitValue() != 0) {
				throw new DocumentExtractionException(CommonMessageConstants.OCR_FAILED);
			}
			return new String(captured, StandardCharsets.UTF_8);
		} catch (InterruptedException exception) {
			terminateAfterInterruption(process);
			Thread.currentThread().interrupt();
			throw new DocumentExtractionException(CommonMessageConstants.OCR_INTERRUPTED, exception);
		} catch (ExecutionException | java.util.concurrent.TimeoutException exception) {
			throw new DocumentExtractionException(CommonMessageConstants.OCR_RESULT_UNAVAILABLE, exception);
		} finally {
			if (process.isAlive()) terminate(process);
			outputReader.shutdownNow();
		}
	}

	/** 検証済みの引数でTesseractを起動する。
	 *
	 * 起動先は管理者設定の標準名または絶対パスに限定し、画像をオプションとして解釈させない。
	 * @param image サーバーが保存した画像
	 * @return 起動したOCRプロセス
	 * @throws IOException 実行ファイルや画像を使用できない場合
	 */
	private Process startTesseract(Path image) throws IOException {
		String command = properties.getTesseractCommand();
		if (command == null || command.isBlank()) {
			throw new DocumentExtractionException(CommonMessageConstants.OCR_COMMAND_MISSING);
		}
		Path executable = Path.of(command);
		Path executableName = executable.getFileName();
		if (!EXECUTABLE_NAMES.contains(command)
				&& (!executable.isAbsolute() || executableName == null
				|| !EXECUTABLE_NAMES.contains(executableName.toString())
				|| !Files.isRegularFile(executable) || !Files.isExecutable(executable))) {
			throw new DocumentExtractionException(CommonMessageConstants.OCR_COMMAND_INVALID);
		}
		String language = properties.getLanguage();
		if (language == null || !Arrays.stream(language.split("\\+", -1))
				.allMatch(name -> LANGUAGE_NAME.matcher(name).matches())) {
			throw new DocumentExtractionException(CommonMessageConstants.OCR_LANGUAGE_INVALID);
		}
		Path input = image.toAbsolutePath().normalize();
		if (!Files.isRegularFile(input, LinkOption.NOFOLLOW_LINKS)) {
			throw new DocumentExtractionException(CommonMessageConstants.OCR_IMAGE_MISSING);
		}

		// シェルやコマンド文字列を使わず、検証した値を独立した引数として渡す。
		return new ProcessBuilder(List.of(command, input.toString(), "stdout", "-l", language)).start();
	}

	private void terminate(Process process) {
		process.destroy();
		try {
			if (!process.waitFor(2, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				process.waitFor(2, TimeUnit.SECONDS);
			}
		} catch (InterruptedException _) {
			process.destroyForcibly();
			Thread.currentThread().interrupt();
		}
	}

	private void terminateAfterInterruption(Process process) {
		boolean interrupted = Thread.interrupted();
		try {
			terminate(process);
		} finally {
			if (interrupted) Thread.currentThread().interrupt();
		}
	}
}
