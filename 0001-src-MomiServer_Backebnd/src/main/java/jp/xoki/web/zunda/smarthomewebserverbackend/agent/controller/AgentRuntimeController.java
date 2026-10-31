package jp.xoki.web.zunda.smarthomewebserverbackend.agent.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.model.AgentPrincipal;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.dto.AgentStatusDto.SnapshotRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.dto.AgentStatusDto.TargetResponse;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.service.AgentAuthorization;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.service.AgentMqttProvisioning;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.service.AgentStatusService;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.service.BatchExecutionRequestService;
import lombok.RequiredArgsConstructor;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.service.AgentRuntimeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants.AgentControllerConstants.SERVER_TIME;
import java.time.Clock;

/** MQTT messages only wake synchronization. Executable content is fetched over authenticated HTTP. */
@RestController
@RequestMapping("/api/v1/agent")
@PreAuthorize("hasRole('AGENT')")
@RequiredArgsConstructor
public class AgentRuntimeController {
    private final AgentRuntimeService runtime;
    private final AgentMqttProvisioning mqtt;
    private final AgentAuthorization authorization;
    private final BatchExecutionRequestService executions;
    private final AgentStatusService statuses;
    @org.springframework.beans.factory.annotation.Value("${app.agent-runtime.working-directory:}")
    private String workingDirectory;

    private final Clock clock;

    @GetMapping("/runtime/environment")
    public Map<String, Object> environment(@AuthenticationPrincipal AgentPrincipal principal) {
        String os = runtime.operatingSystem(principal.agentId());
        return Map.of("shell", "windows".equals(os) ? "cmd.exe" : "/bin/sh",
                "shell_args", "windows".equals(os) ? List.of("/D","/S","/C") : List.of("-c"),
                "working_directory", workingDirectory);
    }

    @GetMapping("/mqtt/settings")
    public AgentMqttProvisioning.Connection mqtt(@AuthenticationPrincipal AgentPrincipal principal) {
        return mqtt.connection(principal);
    }

    @GetMapping("/runtime/sync")
    public Map<String, Object> sync(@AuthenticationPrincipal AgentPrincipal principal) {
        List<UUID> ids = runtime.executionIds(principal);
        return Map.of("execution_ids", ids, SERVER_TIME, OffsetDateTime.now(clock),
                "time_zone", clock.getZone().getId());
    }

    @GetMapping("/batch/executions/{id}/command")
    @Transactional
    public Object command(@AuthenticationPrincipal AgentPrincipal principal, @PathVariable UUID id) {
        authorization.requireExecution(id);
        return Map.of("agent_id", principal.agentId(), "generation", principal.generation(),
                SERVER_TIME, OffsetDateTime.now(clock), "time_zone", clock.getZone().getId(),
                "execution", executions.getDetail(id));
    }

    public record Heartbeat(boolean mqtt_connected) { }

    @PostMapping("/heartbeat")
    public Map<String, Object> heartbeat(@AuthenticationPrincipal AgentPrincipal principal,
            @RequestBody Heartbeat body) {
        runtime.heartbeat(principal, body.mqtt_connected());
        return Map.of(SERVER_TIME, OffsetDateTime.now(clock));
    }

    @GetMapping("/status/targets")
    public TargetResponse statusTargets(@AuthenticationPrincipal AgentPrincipal principal) {
        return new TargetResponse(statuses.targets(principal));
    }

    @PostMapping("/status/snapshot")
    public void statusSnapshot(@AuthenticationPrincipal AgentPrincipal principal,
            @Valid @RequestBody SnapshotRequest body) {
        statuses.save(principal, body);
    }

    public record Unknown(@Size(max = 500) String reason) { }

    @PostMapping("/batch/executions/{id}/unconfirmed")
    @Transactional
    public void unconfirmed(@PathVariable UUID id, @Valid @RequestBody Unknown body) {
        runtime.unconfirmed(id, body.reason());
    }
}
