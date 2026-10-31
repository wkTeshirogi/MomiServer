package jp.xoki.web.zunda.smarthomewebserverbackend.common.constants;

import java.util.Set;
import java.util.regex.Pattern;

/** TesseractOcrEngineで使用する固定値。カテゴリ内の利用元から参照する。 */
public final class TesseractOcrEngineConstants {

	public static final int MAX_OUTPUT_BYTES = 1_048_576;

	public static final Set<String> EXECUTABLE_NAMES = Set.of("tesseract", "tesseract.exe");

	public static final Pattern LANGUAGE_NAME = Pattern.compile("(?:script/)?[A-Za-z]\\w*");

	private TesseractOcrEngineConstants() {
	}
}
