package jp.xoki.web.zunda.smarthomewebserverbackend.auth.dto.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.RoleEnum;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRequestJsonTest {

	private final ObjectMapper mapper = new ObjectMapper();

	@Test
	void authenticationRequestsPopulateTokensPasswordsAndRoles() throws Exception {
		LogoutRequest logout = mapper.readValue("{\"refreshToken\":\"test-token\"}", LogoutRequest.class);
		assertThat(logout.getRefreshToken()).isEqualTo("test-token");

		String json = "{\"email\":\"test@example.com\",\"password\":\"test-password\",\"role\":\"USER\"}";
		RegisterRequest register = mapper.readValue(json, RegisterRequest.class);
		assertThat(register.getEmail()).isEqualTo("test@example.com");
		assertThat(register.getPassword()).isEqualTo("test-password");
		assertThat(register.getRole()).isEqualTo(RoleEnum.USER_ROLE);
		UserSettingsRequest settings = mapper.readValue(json, UserSettingsRequest.class);
		assertThat(settings.getEmail()).isEqualTo("test@example.com");
		assertThat(settings.getPassword()).isEqualTo("test-password");
		assertThat(settings.getRole()).isEqualTo(RoleEnum.USER_ROLE);
	}
}
