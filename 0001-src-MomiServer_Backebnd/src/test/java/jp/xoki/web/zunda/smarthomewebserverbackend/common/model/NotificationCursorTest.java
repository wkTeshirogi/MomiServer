package jp.xoki.web.zunda.smarthomewebserverbackend.common.model;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationCursorTest {
    @Test
    void roundTripsTimestampPrecisionAndEqualDateTieBreaker() {
        NotificationCursor cursor = new NotificationCursor(LocalDateTime.parse("2026-10-26T12:34:56.789"), 10, 100);
        assertThat(NotificationCursor.decode(cursor.encode())).isEqualTo(cursor);
        assertThat(NotificationCursor.decode(null)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "%%%", "2026-10-26T12:34:56|-1|100", "2026-10-26T12:34:56|101|100", "invalid|10|100", "a|b"})
    void invalidCursorCannotFallBackToAnUnboundedQuery(String value) {
        String encoded = Base64.getUrlEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> NotificationCursor.decode(encoded)).isInstanceOf(IllegalArgumentException.class);
    }
}
