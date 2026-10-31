package jp.xoki.web.zunda.smarthomewebserverbackend.status.service;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import jp.xoki.web.zunda.smarthomewebserverbackend.status.dto.StatusDto.Channel;
import jp.xoki.web.zunda.smarthomewebserverbackend.status.dto.StatusDto.TicketResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import static jp.xoki.web.zunda.smarthomewebserverbackend.status.constants.StatusWebSocketTicketServiceConstants.MACHINE_ROLES;
import static jp.xoki.web.zunda.smarthomewebserverbackend.status.constants.StatusWebSocketTicketServiceConstants.SERVICE_ROLES;
import java.time.Clock;

/** 認証済みHTTP要求から短寿命・一回限りのWebSocket接続チケットを発行する。 */
@Service
public class StatusWebSocketTicketService {

	private final ConcurrentMap<String, Ticket> tickets = new ConcurrentHashMap<>();
	private final long ticketTtlSeconds;

	private final Clock clock;

	public StatusWebSocketTicketService(
			@Value("${app.status.websocket.ticket-ttl-seconds:30}") long ticketTtlSeconds, Clock clock
	) {
		this.ticketTtlSeconds = ticketTtlSeconds;
		this.clock = clock;
	}

	public TicketResponse issue(Channel channel, Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			throw new AccessDeniedException("Authentication is required");
		}
		Set<String> roles = authentication.getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.collect(Collectors.toUnmodifiableSet());
		Set<String> allowedRoles = channel == Channel.MACHINES ? MACHINE_ROLES : SERVICE_ROLES;
		if (roles.stream().noneMatch(allowedRoles::contains)) {
			throw new AccessDeniedException("Status channel is not allowed");
		}
		String value = UUID.randomUUID().toString();
		OffsetDateTime expiresAt = OffsetDateTime.now(clock).plusSeconds(ticketTtlSeconds);
		tickets.put(value, new Ticket(channel, authentication.getName(), roles, expiresAt));
		return new TicketResponse(value, channel.path(), expiresAt);
	}

	public Ticket consume(String value, Channel expectedChannel) {
		Ticket ticket = value == null ? null : tickets.remove(value);
		if (ticket == null || ticket.channel() != expectedChannel
				|| !ticket.expiresAt().isAfter(OffsetDateTime.now(clock))) {
			throw new AccessDeniedException("Invalid WebSocket ticket");
		}
		return ticket;
	}

	/** 期限切れ接続チケットの整理。
	 *
	 * 発行・消費と同じServiceが保持するチケットから、共通Clockで期限切れを除去する。
	 */
	public void cleanup() {
		OffsetDateTime now = OffsetDateTime.now(clock);
		tickets.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
	}

	/** WebSocketセッションへ引き継ぐ認証済みチケット情報。 */
	public record Ticket(Channel channel, String username, Set<String> roles, OffsetDateTime expiresAt) {
		public Ticket {
			roles = roles == null ? null : Collections.unmodifiableSet(new LinkedHashSet<>(roles));
		}
	}
}
