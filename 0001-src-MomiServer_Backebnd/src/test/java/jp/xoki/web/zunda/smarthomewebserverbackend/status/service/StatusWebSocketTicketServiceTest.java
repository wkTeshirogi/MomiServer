package jp.xoki.web.zunda.smarthomewebserverbackend.status.service;

import jp.xoki.web.zunda.smarthomewebserverbackend.status.dto.StatusDto.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.time.ApplicationClockFactory;
import jp.xoki.web.zunda.smarthomewebserverbackend.status.dto.StatusDto;

class StatusWebSocketTicketServiceTest {

	private final StatusWebSocketTicketService service = new StatusWebSocketTicketService(30, ApplicationClockFactory.systemClock());

	@Test
	void adminCanIssueAndConsumeMachineTicketOnlyOnce() {
		StatusDto.TicketResponse response = service.issue(Channel.MACHINES, authentication("ADMIN"));

		StatusWebSocketTicketService.Ticket ticket = service.consume(response.ticket(), Channel.MACHINES);

		assertThat(ticket.username()).isEqualTo("test@example.com");
		assertThat(ticket.channel()).isEqualTo(Channel.MACHINES);
		String exceptionInput1 = response.ticket();
		assertThatThrownBy(() -> service.consume(exceptionInput1, Channel.MACHINES))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void userCanIssueServiceTicketButCannotIssueMachineTicket() {
		StatusDto.TicketResponse response = service.issue(Channel.SERVICES, authentication("USER"));

		assertThat(service.consume(response.ticket(), Channel.SERVICES).channel())
				.isEqualTo(Channel.SERVICES);
		UsernamePasswordAuthenticationToken exceptionInput2 = authentication("USER");
		assertThatThrownBy(() -> service.issue(Channel.MACHINES, exceptionInput2))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void ticketCannotBeUsedForAnotherChannel() {
		StatusDto.TicketResponse response = service.issue(Channel.SERVICES, authentication("ADMIN"));

		String exceptionInput3 = response.ticket();
		assertThatThrownBy(() -> service.consume(exceptionInput3, Channel.MACHINES))
				.isInstanceOf(AccessDeniedException.class);
	}

	private UsernamePasswordAuthenticationToken authentication(String role) {
		return new UsernamePasswordAuthenticationToken(
				"test@example.com",
				null,
				List.of(new SimpleGrantedAuthority("ROLE_" + role))
		);
	}
}
