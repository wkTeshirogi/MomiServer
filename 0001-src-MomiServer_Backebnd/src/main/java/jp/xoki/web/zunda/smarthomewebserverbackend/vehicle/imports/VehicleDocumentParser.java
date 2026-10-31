package jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.imports;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

import static jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.constants.VehicleDocumentParserConstants.DATE;
import static jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.constants.VehicleDocumentParserConstants.KNOWN_LABEL_AT_START;
import static jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.constants.VehicleDocumentParserConstants.MONTH;
import static jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.constants.VehicleDocumentParserConstants.PERSONAL_DATA_BOUNDARY;
import static jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.constants.VehicleDocumentParserConstants.VEHICLE_DATA_BOUNDARY;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.constants.VehicleMessageConstants;

/** 車検証にある既知ラベルのみを候補化し、全文を保持しない保守的な解析器。 */
@Component
public class VehicleDocumentParser {

	public ParsedCandidate parse(String source) {
		String normalized = source == null ? "" : Normalizer.normalize(source, Normalizer.Form.NFKC).replaceAll("[\\t ]+", " ")
				.replaceAll(" ?\\r?\\n ?", "\n");
		List<String> warnings = new ArrayList<>();
		String registration = value(normalized, "(?:自動車登録番号又は車両番号|登録番号|車両番号)");
		String chassis = value(normalized, "車台番号");
		if (chassis != null) chassis = chassis.replace(" ", "");
		String vehicleName = value(normalized, "車名");
		if (vehicleName != null) vehicleName = vehicleName.replaceFirst("\\[\\d++\\]$", "").trim();
		String model = value(normalized, "型式");
		String month = parseMonth(value(normalized, "(?:初度登録年月|初度検査年月)"));
		LocalDate issued = parseDate(value(normalized, "(?:登録年月日/交付年月日|交付年月日)"));
		LocalDate expiry = parseDate(value(normalized, "(?:有効期間の満了する日|車検有効期間満了日|有効期間満了日)"));
		List<String> dateRow = certificateDateRow(normalized);
		if (!dateRow.isEmpty()) {
			issued = parseDate(dateRow.get(0));
			month = parseMonth(dateRow.get(1));
			expiry = parseDate(dateRow.get(2));
		}
		Matcher modelRow = Pattern.compile("(?m)^型式 原動機の型式\\n([^\\n]+)$").matcher(normalized);
		if (modelRow.find()) model = cleanValue(modelRow.group(1).split(" ", 2)[0]);
		if (registration == null) warnings.add(VehicleMessageConstants.IMPORT_REGISTRATION_WARNING);
		if (chassis == null) warnings.add(VehicleMessageConstants.IMPORT_CHASSIS_WARNING);
		if (vehicleName == null) warnings.add(VehicleMessageConstants.IMPORT_NAME_WARNING);
		if (expiry == null) warnings.add(VehicleMessageConstants.IMPORT_EXPIRY_WARNING);
		return new ParsedCandidate(VehicleImportCandidate.builder().display_name(vehicleName).registration_number(registration)
				.chassis_number(chassis).vehicle_name(vehicleName).model_code(model).first_registration_month(month)
				.issued_date(issued).inspection_expiry_date(expiry).build(), warnings);
	}

	private String value(String text, String label) {
		Matcher matcher = Pattern.compile("(?:^|\\n)[ \\t]*" + label + "[ \\t]*:?[ \\t]*(?:\\n)?([^\\n]{1,100})",
				Pattern.MULTILINE).matcher(text);
		if (!matcher.find()) return null;
		return cleanValue(matcher.group(1));
	}

	private String cleanValue(String value) {
		String result = value.replaceAll("\\s+", " ").trim();
		if (KNOWN_LABEL_AT_START.matcher(result).find()) return null;
		Matcher personalData = PERSONAL_DATA_BOUNDARY.matcher(result);
		if (personalData.find()) {
			String candidate = result.substring(0, personalData.start()).trim();
			String personalValue = result.substring(personalData.end()).trim();
			if (candidate.isBlank() || personalValue.isBlank()
					|| KNOWN_LABEL_AT_START.matcher(personalValue).find()) return null;
			result = candidate;
		}
		Matcher nextVehicleLabel = VEHICLE_DATA_BOUNDARY.matcher(result);
		if (nextVehicleLabel.find()) result = result.substring(0, nextVehicleLabel.start()).trim();
		return result.isBlank() ? null : result;
	}

	/** 記録事項PDFの横並び3列は、値を同じ列順で対応づける。 */
	private List<String> certificateDateRow(String text) {
		Matcher row = Pattern.compile("(?m)^登録年月日/交付年月日 初度(?:登録|検査)年月 有効期間の満了する日\\n([^\\n]+)$").matcher(text);
		if (!row.find()) return List.of();
		String dateValues = normalizeDateSeparators(row.group(1));
		Matcher dates = Pattern.compile("(" + DATE.pattern() + ")|(" + MONTH.pattern() + ")").matcher(dateValues);
		List<String> values = new ArrayList<>();
		int end = 0;
		while (dates.find()) {
			if (!dateValues.substring(end, dates.start()).isBlank()) return List.of();
			values.add(dates.group());
			end = dates.end();
		}
		if (values.size() != 3 || !dateValues.substring(end).isBlank()) return List.of();
		return values;
	}

	private String parseMonth(String value) {
		if (value == null) return null;
		Matcher matcher = MONTH.matcher(normalizeDateSeparators(value));
		if (!matcher.find()) return null;
		try {
			YearMonth month = YearMonth.of(year(matcher), Integer.parseInt(matcher.group(4)));
			if (matcher.group(1) != null && (!month.atEndOfMonth().isBefore(eraStart(matcher.group(1)))
					&& month.atDay(1).isBefore(eraEnd(matcher.group(1))))) return month.toString();
			return matcher.group(1) == null ? month.toString() : null;
		} catch (RuntimeException _) { return null; }
	}

	private LocalDate parseDate(String value) {
		if (value == null) return null;
		Matcher matcher = DATE.matcher(normalizeDateSeparators(value));
		if (!matcher.find()) return null;
		try {
			LocalDate date = LocalDate.of(year(matcher), Integer.parseInt(matcher.group(4)), Integer.parseInt(matcher.group(5)));
			String era = matcher.group(1);
			return era == null || (!date.isBefore(eraStart(era)) && date.isBefore(eraEnd(era))) ? date : null;
		}
		catch (RuntimeException _) { return null; }
	}

	/** 区切り文字の前後だけを正規化し、数字同士の空白は保持する。 */
	private String normalizeDateSeparators(String value) {
		StringBuilder normalized = new StringBuilder(value.length());
		int index = 0;
		while (index < value.length()) {
			char current = value.charAt(index);
			if (!isDateWhitespace(current)) {
				normalized.append(current);
				index++;
				continue;
			}
			int start = index;
			while (index < value.length() && isDateWhitespace(value.charAt(index))) index++;
			if ((start == 0 || !isDateSeparator(value.charAt(start - 1)))
					&& (index == value.length() || !isDateSeparator(value.charAt(index)))) {
				normalized.append(value, start, index);
			}
		}
		return normalized.toString();
	}

	private boolean isDateWhitespace(char value) {
		return switch (value) {
			case ' ', '\t', '\n', '\u000B', '\f', '\r' -> true;
			default -> false;
		};
	}

	private boolean isDateSeparator(char value) {
		return switch (value) {
			case '年', '/', '月', '日', '.', '-' -> true;
			default -> false;
		};
	}

	private int year(Matcher matcher) {
		if (matcher.group(1) == null) return Integer.parseInt(matcher.group(3));
		int eraYear = "元".equals(matcher.group(2)) ? 1 : Integer.parseInt(matcher.group(2));
		if (eraYear < 1) throw new IllegalArgumentException("Invalid era year");
		return eraStart(matcher.group(1)).getYear() + eraYear - 1;
	}

	private LocalDate eraStart(String era) {
		return switch (era) {
			case "令和" -> LocalDate.of(2019, Month.MAY, 1);
			case "平成" -> LocalDate.of(1989, Month.JANUARY, 8);
			case "昭和" -> LocalDate.of(1926, Month.DECEMBER, 25);
			default -> throw new IllegalArgumentException("Unknown era");
		};
	}

	private LocalDate eraEnd(String era) {
		return switch (era) {
			case "昭和" -> eraStart("平成");
			case "平成" -> eraStart("令和");
			default -> LocalDate.MAX;
		};
	}

	/** 解析結果と確認画面で示す不足項目警告。 */
	public record ParsedCandidate(VehicleImportCandidate candidate, List<String> warnings) {
		public ParsedCandidate {
			warnings = warnings == null ? null : Collections.unmodifiableList(new ArrayList<>(warnings));
		}
	}
}
