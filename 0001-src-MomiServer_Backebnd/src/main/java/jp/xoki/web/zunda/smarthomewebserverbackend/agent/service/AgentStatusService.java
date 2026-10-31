package jp.xoki.web.zunda.smarthomewebserverbackend.agent.service;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.dto.AgentStatusDto.ServiceSnapshot;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.dto.AgentStatusDto.ServiceTarget;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.dto.AgentStatusDto.SnapshotRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.model.AgentPrincipal;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.repository.AgentStatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants.AgentStatusServiceConstants.ALLOWED_CLOCK_SKEW_MINUTES;
import java.time.Clock;

/** 認証済みAgentの監視対象提供と状態スナップショット登録を管理するService。 */
@Service
@RequiredArgsConstructor
public class AgentStatusService {

	private final AgentStatusRepository repository;

	private final Clock clock;

	public List<ServiceTarget> targets(AgentPrincipal principal) {
		return repository.findTargets(principal.deviceId());
	}

	@Transactional
	public void save(AgentPrincipal principal, SnapshotRequest request) {
		if (!repository.lockRegisteredAgent(principal)) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Agent registration is no longer active");
		}
		OffsetDateTime now = OffsetDateTime.now(clock);
		if (request.collected_at().isBefore(now.minusMinutes(ALLOWED_CLOCK_SKEW_MINUTES))
				|| request.collected_at().isAfter(now.plusMinutes(ALLOWED_CLOCK_SKEW_MINUTES))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Collected time is outside the allowed range");
		}
		if (request.storage().stream().anyMatch(value -> value.used_bytes() > value.total_bytes())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Storage usage exceeds capacity");
		}
		Set<String> storageKeys = new HashSet<>();
		if (request.storage().stream().anyMatch(value -> !storageKeys.add(value.storage_key()))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate storage status");
		}

		Set<Long> requestedIds = new HashSet<>();
		for (ServiceSnapshot service : request.services()) {
			if (!requestedIds.add(service.service_id())) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate service status");
			}
		}
		Set<Long> targetIds = repository.findTargetIds(principal.deviceId());
		if (!targetIds.containsAll(requestedIds)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Service does not belong to this Agent");
		}
		repository.save(principal, request);
	}
}
