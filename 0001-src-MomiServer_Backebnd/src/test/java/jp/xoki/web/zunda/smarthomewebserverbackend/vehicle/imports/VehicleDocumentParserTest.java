package jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.imports;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;

class VehicleDocumentParserTest {

	private final VehicleDocumentParser parser = new VehicleDocumentParser();

	@ParameterizedTest
	@ValueSource(strings = {"2026-10-20", "2026 / 10 / 20", "2026 . 10 . 20",
			"2026 年 10 月 20 日", "令和 8 年 10 月 20 日"})
	void preservesSupportedDatesWithSeparatorWhitespace(String date) {
		VehicleImportCandidate candidate = parser.parse("交付年月日: " + date).candidate();
		assertThat(candidate.issued_date()).isEqualTo(LocalDate.of(2026, 10, 20));
	}

	@ParameterizedTest
	@ValueSource(strings = {"2023-11", "2023 / 11", "2023 年 11 月", "令和 5 年 11 月"})
	void preservesSupportedMonthsWithSeparatorWhitespace(String month) {
		VehicleImportCandidate candidate = parser.parse("初度登録年月: " + month).candidate();
		assertThat(candidate.first_registration_month()).isEqualTo("2023-11");
	}

	@ParameterizedTest
	@ValueSource(strings = {"2026 10 20", "2026年1 0月20日", "2026年2月30日", "令和0年10月20日"})
	void rejectsMissingSeparatorsBrokenDigitsAndInvalidDates(String date) {
		assertThat(parser.parse("交付年月日: " + date).candidate().issued_date()).isNull();
	}

	@Test
	void parsesWesternTableDatesWithWhitespaceAroundSeparators() {
		VehicleImportCandidate candidate = parser.parse("""
				登録年月日/交付年月日 初度登録年月 有効期間の満了する日
				2023 / 11 / 6 2023 - 11 2026 . 11 . 5
				""").candidate();
		assertThat(candidate.issued_date()).isEqualTo(LocalDate.of(2023, 11, 6));
		assertThat(candidate.first_registration_month()).isEqualTo("2023-11");
		assertThat(candidate.inspection_expiry_date()).isEqualTo(LocalDate.of(2026, 11, 5));
	}

	@Test
	void parsesViewerCertificateTableWithFullWidthValuesAndJapaneseDates() {
		VehicleImportCandidate candidate = parser.parse("""
			自動車登録番号又は車両番号
			 テスト ５０２ す １２３４
			車台番号
			 ＴＥＳＴ１１０Ｆ５０００００１
			登録年月日／交付年月日 初度登録年月 有効期間の満了する日
			 令和 ５ 年 １１ 月 ６ 日 令和 ５ 年 １１ 月 令和 ８ 年 １１ 月 ５ 日
			２.所有者・使用者情報
			 所有者の氏名又は名称
			 保存しない氏名
			 所有者の住所
			 保存しない住所
			３.車両詳細情報
			車名
			 テストメーカー [９９９]
			型式 原動機の型式
			 不明 １２６Ａ５.０００
			""").candidate();

		assertThat(candidate.registration_number()).isEqualTo("テスト 502 す 1234");
		assertThat(candidate.chassis_number()).isEqualTo("TEST110F5000001");
		assertThat(candidate.vehicle_name()).isEqualTo("テストメーカー");
		assertThat(candidate.model_code()).isEqualTo("不明");
		assertThat(candidate.first_registration_month()).isEqualTo("2023-11");
		assertThat(candidate.issued_date()).isEqualTo(LocalDate.of(2023, 11, 6));
		assertThat(candidate.inspection_expiry_date()).isEqualTo(LocalDate.of(2026, 11, 5));
		assertThat(candidate.toString()).doesNotContain("保存しない");
	}

	@Test
	void rejectsAmbiguousTableDateRowAndPersonalLabelAsFollowingValue() {
		VehicleImportCandidate candidate = parser.parse("""
			登録年月日/交付年月日 初度登録年月 有効期間の満了する日
			 令和5年11月6日 令和8年11月5日
			車名
			所有者の氏名又は名称: 保存しない氏名
			""").candidate();
		assertThat(candidate.vehicle_name()).isNull();
		assertThat(candidate.issued_date()).isNull();
		assertThat(candidate.inspection_expiry_date()).isNull();
	}

	@Test
	void convertsEraFirstYearAndRejectsInvalidEraDates() {
		VehicleImportCandidate candidate = parser.parse("交付年月日: 平成元年1月8日\n有効期間満了日: 令和元年5月1日\n初度登録年月: 昭和48年6月").candidate();
		assertThat(candidate.issued_date()).isEqualTo(LocalDate.of(1989, 1, 8));
		assertThat(candidate.inspection_expiry_date()).isEqualTo(LocalDate.of(2019, 5, 1));
		assertThat(candidate.first_registration_month()).isEqualTo("1973-06");
		assertThat(parser.parse("交付年月日: 平成元年1月7日\n有効期間満了日: 令和元年4月30日").candidate().issued_date()).isNull();
		assertThat(parser.parse("有効期間満了日: 令和元年4月30日").candidate().inspection_expiry_date()).isNull();
	}

	@Test
	void retainsOnlyLabeledVehicleCandidates() {
		VehicleDocumentParser.ParsedCandidate result = parser.parse("""
			所有者 氏名: 保持しない情報
			登録番号: 品川 300 あ 12-34
			車台番号: TEST-CHASSIS-001
			車名: テスト車
			型式: TEST-MODEL
			初度登録年月: 2025年10月
			交付年月日: 2026年10月1日
			有効期間の満了する日: 2027年10月1日
			""");

		assertThat(result.candidate().registration_number()).isEqualTo("品川 300 あ 12-34");
		assertThat(result.candidate().chassis_number()).isEqualTo("TEST-CHASSIS-001");
		assertThat(result.candidate().vehicle_name()).isEqualTo("テスト車");
		assertThat(result.candidate().first_registration_month()).isEqualTo("2025-10");
		assertThat(result.candidate().inspection_expiry_date()).isEqualTo(LocalDate.of(2027, 10, 1));
		assertThat(result.candidate().issued_date()).isEqualTo(LocalDate.of(2026, 10, 1));
	}

	@Test
	void warnsWhenRequiredCandidatesAreMissing() {
		assertThat(parser.parse("住所: 保持しない情報").warnings()).contains("車台番号を確認して入力してください。",
				"車検有効期間満了日を確認して入力してください。");
	}

	@Test
	void handlesWhitespaceAndManufacturerCodeWithoutChangingCandidates() {
		VehicleImportCandidate candidate = parser.parse("車名:\t テスト車 [999]  \r\n  交付年月日: 2026\t/\t10\f/\f20\r\n").candidate();
		assertThat(candidate.vehicle_name()).isEqualTo("テスト車");
		assertThat(candidate.issued_date()).isEqualTo(LocalDate.of(2026, 10, 20));
		assertThat(parser.parse("車名: テスト車 [999x]").candidate().vehicle_name()).isEqualTo("テスト車 [999x]");
	}

	@Test
	void longInvalidDateRowDoesNotCauseRegexBacktracking() {
		String source = "登録年月日/交付年月日 初度登録年月 有効期間の満了する日\n2026"
				+ "\f".repeat(100_000) + "invalid";
		assertTimeout(Duration.ofSeconds(5), () -> {
			VehicleImportCandidate candidate = parser.parse(source).candidate();
			assertThat(candidate.issued_date()).isNull();
			assertThat(candidate.first_registration_month()).isNull();
			assertThat(candidate.inspection_expiry_date()).isNull();
		});
	}

	@Test
	void removesPersonalDataThatAppearsAfterCandidateOnSameLine() {
		VehicleDocumentParser.ParsedCandidate result = parser.parse(
				"車名: テスト車 所有者の氏名又は名称: 秘密氏名\n車台番号: TEST-001 使用者住所: 秘密住所");

		assertThat(result.candidate().vehicle_name()).isEqualTo("テスト車");
		assertThat(result.candidate().chassis_number()).isEqualTo("TEST-001");
		assertThat(result.candidate().vehicle_name()).doesNotContain("秘密氏名");
		assertThat(result.candidate().chassis_number()).doesNotContain("秘密住所");
	}

	@Test
	void delimitsColonlessQualifiedPersonalLabelsOnSameLine() {
		VehicleDocumentParser.ParsedCandidate result = parser.parse("""
			車名: テスト車 所有者の氏名又は名称 山田太郎
			車台番号: TEST-001 使用者住所 東京都新宿区
			""");

		assertThat(result.candidate().vehicle_name()).isEqualTo("テスト車");
		assertThat(result.candidate().chassis_number()).isEqualTo("TEST-001");
	}

	@Test
	void delimitsPersonalLabelsWithoutOwnerOrUserQualifier() {
		VehicleDocumentParser.ParsedCandidate result = parser.parse("""
			車名: テスト車 氏名又は名称 山田太郎
			登録番号: 品川 300 あ 12-34 住所 東京都新宿区
			型式: TEST-MODEL 名称 山田商事
			交付年月日: 2026年10月1日 氏名 山田太郎
			""");

		assertThat(result.candidate().vehicle_name()).isEqualTo("テスト車");
		assertThat(result.candidate().registration_number()).isEqualTo("品川 300 あ 12-34");
		assertThat(result.candidate().model_code()).isEqualTo("TEST-MODEL");
		assertThat(result.candidate().issued_date()).isEqualTo(LocalDate.of(2026, 10, 1));
	}

	@Test
	void rejectsCandidateWhenPersonalLabelHasNoFollowingValue() {
		VehicleDocumentParser.ParsedCandidate result = parser.parse("車名: 山田太郎 氏名又は名称");

		assertThat(result.candidate().vehicle_name()).isNull();
		assertThat(result.candidate().display_name()).isNull();
	}
}
