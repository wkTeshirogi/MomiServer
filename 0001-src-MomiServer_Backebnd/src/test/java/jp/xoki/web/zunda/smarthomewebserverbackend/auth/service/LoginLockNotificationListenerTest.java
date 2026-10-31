package jp.xoki.web.zunda.smarthomewebserverbackend.auth.service;

import java.time.LocalDateTime;
import jp.xoki.web.zunda.smarthomewebserverbackend.auth.model.LoginLockedEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

class LoginLockNotificationListenerTest {
    @Test
    void notificationFailureCannotChangeCommittedLoginRejection() {
        LoginLockNotificationService service = mock(LoginLockNotificationService.class);
        LoginLockedEvent event = new LoginLockedEvent(10L, LocalDateTime.of(2026, 10, 26, 12, 15));
        doThrow(new IllegalStateException("Simulated notification commit failure")).when(service).send(event);
        assertThatCode(() -> new LoginLockNotificationListener(service).onLocked(event)).doesNotThrowAnyException();
        verify(service).send(event);
    }
}
