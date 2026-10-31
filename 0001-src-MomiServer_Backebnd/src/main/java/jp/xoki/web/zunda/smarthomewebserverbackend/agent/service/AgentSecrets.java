package jp.xoki.web.zunda.smarthomewebserverbackend.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.security.TokenHashes;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.config.AgentAuthProperties;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.dto.AgentAuthDto.TokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import static jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants.AgentSecretsConstants.GCM_TAG_BITS;
import static jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants.AgentSecretsConstants.IV_BYTES;
import static jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants.AgentSecretsConstants.MAX_NONCE_LENGTH;
import static jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants.AgentSecretsConstants.MIN_ACCESS_MINUTES;
import static jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants.AgentSecretsConstants.TOKEN_BYTES;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants.AgentMessageConstants;

/** 標準JWS EdDSAによる保有証明とAES-GCMによる短期再送キャッシュ。 */
@Component
@RequiredArgsConstructor
public class AgentSecrets {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final AgentAuthProperties properties;
    private final ObjectMapper mapper;

    public static String randomToken(String prefix) {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String value) {
        return TokenHashes.sha256(value);
    }

    /** ハッシュを定時間比較する。
     *
     * 計算済みの固定長ハッシュを先頭にし、保存値の欠損は不一致として扱う。
     * @param computedHash 計算済みハッシュ
     * @param storedHash DBに保存されたハッシュ
     * @return 両方のハッシュが存在し、正規化せず一致する場合はtrue
     */
    static boolean hashEquals(String computedHash, String storedHash) {
        return TokenHashes.matches(computedHash, storedHash);
    }

    public void requireConfigured() {
        key();
        if (properties.getAccessMinutes() < MIN_ACCESS_MINUTES || properties.getRefreshDays() < 1
                || properties.getRegistrationMinutes() < 1 || properties.getRecoveryHours() < 1
                || properties.getRefreshRecoveryMinutes() < 1 || properties.getRequestsPerMinute() < 1) {
            throw new AgentAuthException(HttpStatus.SERVICE_UNAVAILABLE.value(), "AGENT_AUTH_NOT_CONFIGURED",
                    AgentMessageConstants.AUTH_CONFIGURATION_INVALID);
        }
    }

    private SecretKeySpec key() {
        try {
            byte[] bytes = Base64.getDecoder().decode(properties.getResponseEncryptionKey());
            if (bytes.length != TOKEN_BYTES) {
                throw new IllegalArgumentException();
            }
            return new SecretKeySpec(bytes, "AES");
        } catch (IllegalArgumentException _) {
            throw new AgentAuthException(HttpStatus.SERVICE_UNAVAILABLE.value(), "AGENT_AUTH_NOT_CONFIGURED",
                    AgentMessageConstants.AUTH_KEY_MISSING);
        }
    }

    /**
     * 署名と用途・要求・資格情報の結合を検証する。
     * @param encodedKey SPKI形式の公開鍵
     * @param proof JWS保有証明
     * @param purpose registerまたはrefresh
     * @param requestId 再送を識別する要求ID
     * @param credential コードまたは更新トークン
     * @return 署名済みの使い捨てnonce
     */
    public String verifyProof(String encodedKey, String proof, String purpose, UUID requestId, String credential) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
            PublicKey publicKey = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(keyBytes));
            Jws<Claims> jws = Jwts.parser().verifyWith(publicKey).requireAudience("momiserver-agent-" + purpose)
                    .require("requestId", requestId.toString()).require("credentialHash", hash(credential))
                    .build().parseSignedClaims(proof);
            if (!"EdDSA".equals(jws.getHeader().getAlgorithm())) {
                throw AgentAuthException.unauthorized();
            }
            String nonce = jws.getPayload().get("nonce", String.class);
            if (nonce == null || nonce.length() > MAX_NONCE_LENGTH) {
                throw AgentAuthException.unauthorized();
            }
            return nonce;
        } catch (java.security.GeneralSecurityException | RuntimeException _) {
            throw AgentAuthException.unauthorized();
        }
    }

    public String seal(Object response, String context) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(mapper.writeValueAsBytes(response));
            return Base64.getEncoder().encodeToString(iv) + "." + Base64.getEncoder().encodeToString(encrypted);
        } catch (AgentAuthException exception) {
            throw exception;
        } catch (Exception _) {
            throw new IllegalStateException("Could not protect agent response");
        }
    }

    public TokenResponse open(String value, String context) {
        return open(value, context, TokenResponse.class);
    }

    public <T> T open(String value, String context, Class<T> type) {
        try {
            String[] parts = value.split("\\.");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(),
                    new GCMParameterSpec(GCM_TAG_BITS, Base64.getDecoder().decode(parts[0])));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            return mapper.readValue(cipher.doFinal(Base64.getDecoder().decode(parts[1])), type);
        } catch (java.security.GeneralSecurityException | java.io.IOException | RuntimeException _) {
            throw new AgentAuthException(HttpStatus.CONFLICT.value(), "AGENT_RECOVERY_UNAVAILABLE", AgentMessageConstants.RECOVERY_REQUIRES_REREGISTRATION);
        }
    }
}
