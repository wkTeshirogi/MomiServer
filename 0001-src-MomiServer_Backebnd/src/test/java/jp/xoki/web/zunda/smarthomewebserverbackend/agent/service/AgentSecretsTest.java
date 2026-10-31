package jp.xoki.web.zunda.smarthomewebserverbackend.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.config.AgentAuthProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.security.KeyPair;

class AgentSecretsTest {

	private final AgentAuthProperties properties = new AgentAuthProperties();
	private final AgentSecrets secrets = new AgentSecrets(properties, new ObjectMapper());

	@Test
	void matchingHashIsAcceptedWithoutNormalization() {
		String hash = AgentSecrets.hash("test-token");
		assertThat(AgentSecrets.hashEquals(hash, hash)).isTrue();
		assertThat(AgentSecrets.hashEquals(hash, hash.toUpperCase(java.util.Locale.ROOT))).isFalse();
		assertThat(AgentSecrets.hashEquals(hash, " " + hash)).isFalse();
		assertThat(AgentSecrets.hashEquals(hash, hash + " ")).isFalse();
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 31, 63})
	void hashMismatchAtAnyPositionIsRejected(int position) {
		String hash = AgentSecrets.hash("test-token");
		char[] changed = hash.toCharArray();
		changed[position] = changed[position] == '0' ? '1' : '0';
		assertThat(AgentSecrets.hashEquals(hash, new String(changed))).isFalse();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"not-a-hash", "0", "fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"})
	void missingOrMalformedStoredHashFailsClosed(String stored) {
		assertThat(AgentSecrets.hashEquals(AgentSecrets.hash("test-token"), stored)).isFalse();
		assertThat(AgentSecrets.hashEquals(null, stored)).isFalse();
	}

	@Test
	void validProofStillBindsPurposeRequestAndCredential() throws Exception {
		KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
		UUID requestId = UUID.randomUUID();
		String credential = "test-registration-code";
		String proof = Jwts.builder().audience().add("momiserver-agent-register").and()
				.claim("requestId", requestId.toString())
				.claim("credentialHash", AgentSecrets.hash(credential))
				.claim("nonce", "test-nonce").signWith(keyPair.getPrivate()).compact();
		String key = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
		assertThat(secrets.verifyProof(key, proof, "register", requestId, credential)).isEqualTo("test-nonce");
		AgentAuthException exception = assertThrows(AgentAuthException.class,
				() -> secrets.verifyProof(key, proof, "refresh", requestId, credential));
		assertThat(exception.status()).isEqualTo(401);
		assertThat(exception.getCause()).isNull();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"not-base64!", "aGVsbG8="})
	void malformedKeysFailClosedWithoutLeakingParsingFailures(String key) {
		UUID requestId = UUID.randomUUID();
		AgentAuthException exception = assertThrows(AgentAuthException.class,
				() -> secrets.verifyProof(key, "invalid-proof", "register", requestId, "credential"));
		assertThat(exception.status()).isEqualTo(401);
		assertThat(exception.code()).isEqualTo("AGENT_AUTHENTICATION_FAILED");
		assertThat(exception.getCause()).isNull();
	}

	@Test
	void encryptedResponsesRoundTripButRejectTheWrongContext() {
		properties.setResponseEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
		String encrypted = secrets.seal("test-response", "test-context");
		assertThat(secrets.open(encrypted, "test-context", String.class)).isEqualTo("test-response");
		AgentAuthException exception = assertThrows(AgentAuthException.class,
				() -> secrets.open(encrypted, "another-context", String.class));
		assertThat(exception.status()).isEqualTo(409);
		assertThat(exception.getCause()).isNull();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"invalid", "a.b", ".."})
	void malformedRecoveryValuesUseTheExistingRecoveryError(String value) {
		properties.setResponseEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
		AgentAuthException exception = assertThrows(AgentAuthException.class,
				() -> secrets.open(value, "test-context", String.class));
		assertThat(exception.status()).isEqualTo(409);
		assertThat(exception.code()).isEqualTo("AGENT_RECOVERY_UNAVAILABLE");
		assertThat(exception.getCause()).isNull();
	}
}
