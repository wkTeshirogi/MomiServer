package jp.xoki.web.zunda.smarthomewebserverbackend.common.config;

import com.fasterxml.jackson.core.ErrorReportConfiguration;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.async.ByteArrayFeeder;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JacksonSecurityRegressionTest {

	@Test
	void invalidDataInputTokenHasBoundedErrorMessage() throws Exception {
		JsonFactory factory = boundedErrorFactory();
		byte[] input = ("t" + "x".repeat(4096) + " ").getBytes(StandardCharsets.UTF_8);
		try (DataInputStream stream = new DataInputStream(new ByteArrayInputStream(input));
				JsonParser parser = factory.createParser((DataInput) stream)) {
			JsonParseException exception = assertThrows(JsonParseException.class, parser::nextToken);
			assertThat(exception.getOriginalMessage()).hasSizeLessThan(512);
		}
	}

	@Test
	void invalidStreamTokenAlsoHasBoundedErrorMessage() throws Exception {
		byte[] input = ("t" + "x".repeat(4096) + " ").getBytes(StandardCharsets.UTF_8);
		try (JsonParser parser = boundedErrorFactory().createParser(new ByteArrayInputStream(input))) {
			JsonParseException exception = assertThrows(JsonParseException.class, parser::nextToken);
			assertThat(exception.getOriginalMessage()).hasSizeLessThan(512);
		}
	}

	@Test
	void chunkedNumberCannotBypassReadLimitBeforeEndOfInput() throws Exception {
		JsonFactory factory = JsonFactory.builder()
				.streamReadConstraints(StreamReadConstraints.builder().maxNumberLength(32).build())
				.build();
		byte[] chunk = "1234567890123456".getBytes(StandardCharsets.UTF_8);
		try (JsonParser parser = factory.createNonBlockingByteArrayParser()) {
			ByteArrayFeeder feeder = (ByteArrayFeeder) parser.getNonBlockingInputFeeder();
			assertThatThrownBy(() -> {
				for (int i = 0; i < 32; i++) {
					feeder.feedInput(chunk, 0, chunk.length);
					parser.nextToken();
				}
			}).isInstanceOf(StreamConstraintsException.class);
		}
	}

	@Test
	void ordinaryDataInputStillParses() throws Exception {
		byte[] input = "{\"quantity\":2} ".getBytes(StandardCharsets.UTF_8);
		try (DataInputStream stream = new DataInputStream(new ByteArrayInputStream(input));
				JsonParser parser = boundedErrorFactory().createParser((DataInput) stream)) {
			assertThat(parser.nextToken()).isEqualTo(JsonToken.START_OBJECT);
			assertThat(parser.nextToken()).isEqualTo(JsonToken.FIELD_NAME);
			assertThat(parser.currentName()).isEqualTo("quantity");
			assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_NUMBER_INT);
			assertThat(parser.getIntValue()).isEqualTo(2);
			assertThat(parser.nextToken()).isEqualTo(JsonToken.END_OBJECT);
		}
	}

	private JsonFactory boundedErrorFactory() {
		return JsonFactory.builder()
				.errorReportConfiguration(ErrorReportConfiguration.builder().maxErrorTokenLength(64).build())
				.build();
	}
}
