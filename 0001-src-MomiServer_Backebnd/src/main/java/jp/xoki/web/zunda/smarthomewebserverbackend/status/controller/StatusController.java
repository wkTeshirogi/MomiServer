package jp.xoki.web.zunda.smarthomewebserverbackend.status.controller;

import jakarta.validation.Valid;
import jp.xoki.web.zunda.smarthomewebserverbackend.status.dto.StatusDto.Channel;
import jp.xoki.web.zunda.smarthomewebserverbackend.status.dto.StatusDto.MachineStatus;
import jp.xoki.web.zunda.smarthomewebserverbackend.status.dto.StatusDto.ServiceStatus;
import jp.xoki.web.zunda.smarthomewebserverbackend.status.dto.StatusDto.TicketRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.status.dto.StatusDto.TicketResponse;
import jp.xoki.web.zunda.smarthomewebserverbackend.status.service.StatusSnapshotService;
import jp.xoki.web.zunda.smarthomewebserverbackend.status.service.StatusWebSocketTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** マシン・サービス状態の初期取得とWebSocket接続チケットを提供するController。 */
@RestController
@RequestMapping("/api/status")
@RequiredArgsConstructor
public class StatusController {

	private final StatusSnapshotService snapshots;
	private final StatusWebSocketTicketService tickets;

	@GetMapping("/machines")
	@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
	public List<MachineStatus> machines() {
		return snapshots.machines();
	}

	@GetMapping("/services")
	@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'USER')")
	public List<ServiceStatus> services() {
		return snapshots.services();
	}

	@PostMapping("/websocket-ticket")
	public TicketResponse ticket(@Valid @RequestBody TicketRequest request, Authentication authentication) {
		return tickets.issue(Channel.from(request.channel()), authentication);
	}
}
