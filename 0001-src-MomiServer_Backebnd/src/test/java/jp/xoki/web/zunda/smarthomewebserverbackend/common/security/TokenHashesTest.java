package jp.xoki.web.zunda.smarthomewebserverbackend.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenHashesTest {

	@Test
	void standardVectorsAndNullContractArePreserved() {
		assertThat(TokenHashes.sha256("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
		assertThat(TokenHashes.sha256("")).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
		assertThatThrownBy(() -> TokenHashes.sha256(null)).isInstanceOf(NullPointerException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = {"abc", " refresh-token ", "日本語-token", "ABC", "a\nb"})
	void existingSessionHashesKeepTheirExactUtf8Encoding(String value) throws Exception {
		StringBuilder legacy = new StringBuilder();
		for (byte part : MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))) {
			legacy.append(String.format(Locale.ROOT, "%02x", part));
		}
		assertThat(TokenHashes.sha256(value)).isEqualTo(legacy.toString()).matches("[0-9a-f]{64}");
	}

	@Test
	void comparisonDoesNotNormalizeMissingOrMalformedStoredValues() {
		String computed = TokenHashes.sha256("abc");
		assertThat(TokenHashes.matches(computed, computed)).isTrue();
		assertThat(TokenHashes.matches(computed, computed.toUpperCase(Locale.ROOT))).isFalse();
		assertThat(TokenHashes.matches(computed, " " + computed)).isFalse();
		assertThat(TokenHashes.matches(computed, null)).isFalse();
		assertThat(TokenHashes.matches(null, computed)).isFalse();
		assertThat(TokenHashes.matches(computed, "invalid")).isFalse();
	}

	@Test
	void digestStateIsNotSharedAcrossConcurrentCalls() {
		String expected = TokenHashes.sha256("parallel-token");
		assertThat(IntStream.range(0, 500).parallel().mapToObj(ignored -> TokenHashes.sha256("parallel-token")))
				.isNotEmpty().allMatch(expected::equals);
	}
}
