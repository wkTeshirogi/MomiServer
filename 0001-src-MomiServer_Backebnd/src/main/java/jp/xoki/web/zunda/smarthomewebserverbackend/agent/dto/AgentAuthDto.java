package jp.xoki.web.zunda.smarthomewebserverbackend.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 登録・認証APIの入出力。秘密を含む要求はログへ出力しない。 */
public final class AgentAuthDto {
    private AgentAuthDto() { }

    @io.swagger.v3.oas.annotations.media.Schema(name = "AgentRegisterRequest")
    public record RegisterRequest(
            @NotBlank @Size(max = 100) String registrationCode,
            @NotNull UUID registrationRequestId,
            @NotBlank @Size(max = 128) String publicKey,
            @NotBlank @Size(max = 2048) String proof,
            @NotBlank @Size(max = 255) String hostname,
            @NotBlank @Size(max = 40) String os,
            @NotBlank @Size(max = 40) String agentVersion) { }

    @io.swagger.v3.oas.annotations.media.Schema(name = "AgentRefreshRequest")
    public record RefreshRequest(
            @NotBlank @Size(max = 100) String refreshToken,
            @NotNull UUID refreshRequestId,
            @NotBlank @Size(max = 2048) String proof) { }

    public record StatusRequest(
            @NotBlank @Pattern(regexp = "disabled|pending") String status,
            @NotBlank @Size(max = 500) String reason) { }

    public record CodeResponse(UUID agentId, String registrationCode, OffsetDateTime expiresAt) { }
    public record ChallengeResponse(String nonce, OffsetDateTime expiresAt) { }
    public record MqttSettings(boolean enabled, String status, String clientId) { }
    public record TokenResponse(UUID agentId, long deviceId, long generation,
            String tokenType, String accessToken, OffsetDateTime accessExpiresAt,
            String refreshToken, OffsetDateTime refreshExpiresAt, MqttSettings mqtt) { }
    public record AgentStatus(UUID agentId, long deviceId, long generation, String status,
            String mqttClientId, OffsetDateTime codeExpiresAt, OffsetDateTime registeredAt,
            OffsetDateTime lastSeenAt, String mqttStatus, OffsetDateTime heartbeatAt,
            String connectionStatus, boolean mqttConnected) { }
}
