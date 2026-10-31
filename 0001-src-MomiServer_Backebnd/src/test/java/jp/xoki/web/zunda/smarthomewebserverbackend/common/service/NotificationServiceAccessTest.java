package jp.xoki.web.zunda.smarthomewebserverbackend.common.service;

import jp.xoki.web.zunda.jooq.tables.records.MUsersRecord;
import jp.xoki.web.zunda.smarthomewebserverbackend.auth.repository.UserRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.ResourceNotFoundException;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceAccessTest {

	private final NotificationRepository notifications = mock(NotificationRepository.class);
	private final UserRepository users = mock(UserRepository.class);
	private final NotificationService service = new NotificationService(notifications, users);

	@BeforeEach
	void setUp() {
		MUsersRecord user = new MUsersRecord();
		user.setId(42L);
		when(users.findByEmail("reader@example.com")).thenReturn(Optional.of(user));
	}

	@ParameterizedTest
	@CsvSource({"USER,false,false", "OPERATOR,true,false", "ADMIN,true,true"})
	void notificationQueriesUseTheRoleVisibilityScope(String role, boolean machine, boolean master) {
		TestingAuthenticationToken authentication = new TestingAuthenticationToken("reader@example.com", null, "ROLE_" + role);
		when(notifications.countUnreadByUserId(42L, machine, master)).thenReturn(3);
		service.list(authentication, 10, 20);
		service.listUnread(authentication, 10, 30);
		assertThat(service.countUnread(authentication).unread_count()).isEqualTo(3);

		verify(notifications).findByUserId(42L, 10, 20, machine, master);
		verify(notifications).findUnreadByUserId(42L, 10, 30, machine, master);
		verify(notifications).countUnreadByUserId(42L, machine, master);
	}

	@ParameterizedTest
	@ValueSource(strings = {"USER", "OPERATOR", "ADMIN"})
	void notificationQueriesRejectNegativeOffset(String role) {
		TestingAuthenticationToken authentication = new TestingAuthenticationToken("reader@example.com", null, "ROLE_" + role);
		assertThatThrownBy(() -> service.list(authentication, 20, -1))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = {"USER", "OPERATOR", "ADMIN"})
	void everyRoleCanUpdateOnlyItsOwnNotification(String role) {
		TestingAuthenticationToken authentication = new TestingAuthenticationToken("reader@example.com", null, "ROLE_" + role);
		UUID notificationId = UUID.randomUUID();
		when(notifications.markDelivered(notificationId, 42L)).thenReturn(1);
		when(notifications.markRead(notificationId, 42L)).thenReturn(1);
		when(notifications.dismiss(notificationId, 42L)).thenReturn(1);

		service.markDelivered(authentication, notificationId);
		service.markRead(authentication, notificationId);
		service.dismiss(authentication, notificationId);

		verify(notifications).markDelivered(notificationId, 42L);
		verify(notifications).markRead(notificationId, 42L);
		verify(notifications).dismiss(notificationId, 42L);
	}

	@ParameterizedTest
	@ValueSource(strings = {"USER", "OPERATOR", "ADMIN"})
	void everyRoleReceivesNotFoundForAnOutOfScopeNotification(String role) {
		TestingAuthenticationToken authentication = new TestingAuthenticationToken("reader@example.com", null, "ROLE_" + role);
		UUID notificationId = UUID.randomUUID();
		assertThatThrownBy(() -> service.markRead(authentication, notificationId))
				.isInstanceOf(ResourceNotFoundException.class);
		verify(notifications).markRead(notificationId, 42L);
	}
}
