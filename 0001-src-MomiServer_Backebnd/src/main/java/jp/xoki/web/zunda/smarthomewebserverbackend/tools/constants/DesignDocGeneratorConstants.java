package jp.xoki.web.zunda.smarthomewebserverbackend.tools.constants;

import java.util.List;

/** DesignDocGeneratorで使用する固定値。カテゴリ内の利用元から参照する。 */
public final class DesignDocGeneratorConstants {

	// ────────────────────────────────────────────────
	public static final String SOURCE_ROOT   = "src/main/java";

	public static final String OUTPUT_ROOT   = "docs/design";

	public static final String BASE_PACKAGE  = "jp/xoki/web/zunda/smarthomewebserverbackend";

	// 解析対象レイヤーとその出力サブディレクトリ
	public static final List<List<String>> LAYERS = List.of(
			List.of("auth", "auth"),
			List.of("batch", "batch"),
			List.of("common", "common"),
			List.of("dashboard", "dashboard"),
			List.of("device", "device"),
			List.of("user", "user"));

	public static final String TABLE_CODE_CELL_END = "` |\n";


	private DesignDocGeneratorConstants() {
	}
}
