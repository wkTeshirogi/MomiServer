package jp.xoki.web.zunda.smarthomewebserverbackend.agent.controller;

import jakarta.validation.Valid;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.dto.AgentAuthDto.AgentStatus;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.dto.AgentAuthDto.ChallengeResponse;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.dto.AgentAuthDto.RegisterRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.dto.AgentAuthDto.RefreshRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.dto.AgentAuthDto.TokenResponse;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.model.AgentPrincipal;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.repository.AgentAuthRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.service.AgentAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Agent初回登録と資格情報の更新。登録コードはJSON本文で受信する。 */
@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
public class AgentAuthController {
    private final AgentAuthService service;
    private final AgentAuthRepository repository;

    @PostMapping("/auth/challenge")
    public ResponseEntity<ChallengeResponse> challenge() {
        return noStore(service.challenge());
    }

    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return noStore(service.register(request));
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return noStore(service.refresh(request));
    }

    @GetMapping("/me")
    public ResponseEntity<AgentStatus> me(@AuthenticationPrincipal AgentPrincipal principal) {
        repository.seen(principal.agentId());
        return noStore(service.status(principal.deviceId()));
    }

    private static <T> ResponseEntity<T> noStore(T value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);
    }
}
