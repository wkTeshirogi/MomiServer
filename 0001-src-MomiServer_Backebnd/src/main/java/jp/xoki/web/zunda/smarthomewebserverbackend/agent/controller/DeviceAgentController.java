package jp.xoki.web.zunda.smarthomewebserverbackend.agent.controller;

import jakarta.validation.Valid;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.dto.AgentAuthDto.AgentStatus;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.dto.AgentAuthDto.CodeResponse;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.dto.AgentAuthDto.StatusRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.service.AgentAuthService;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.security.annotation.AdminOnly;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/** 端末マスタを起点とする管理者専用のAgent登録管理。 */
@RestController
@AdminOnly
@RequestMapping("/api/master/device/{deviceId}/agent")
@RequiredArgsConstructor
public class DeviceAgentController {
    private final AgentAuthService service;

    @GetMapping
    public AgentStatus status(@PathVariable long deviceId) {
        return service.status(deviceId);
    }

    @PostMapping("/registration-code")
    public ResponseEntity<CodeResponse> issue(@PathVariable long deviceId, Principal principal) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore()).body(service.issueCode(deviceId, principal.getName()));
    }

    @PostMapping("/status")
    public AgentStatus status(@PathVariable long deviceId, @Valid @RequestBody StatusRequest request,
            Principal principal) {
        return service.changeStatus(deviceId, request, principal.getName());
    }
}
