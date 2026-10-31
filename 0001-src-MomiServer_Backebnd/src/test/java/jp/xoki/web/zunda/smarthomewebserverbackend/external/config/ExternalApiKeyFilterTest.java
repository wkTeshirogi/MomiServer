package jp.xoki.web.zunda.smarthomewebserverbackend.external.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.SecurityErrorResponseHandler;
import jp.xoki.web.zunda.smarthomewebserverbackend.external.constants.ExternalApiConstants;
import jp.xoki.web.zunda.smarthomewebserverbackend.external.dto.ExternalApiKeyDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.external.service.ExternalApiKeyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.time.ApplicationClockFactory;
import org.springframework.security.core.Authentication;

class ExternalApiKeyFilterTest {
    private final ExternalApiKeyService service = mock(ExternalApiKeyService.class);
    private final ExternalApiKeyFilter filter = new ExternalApiKeyFilter(service,
            new SecurityErrorResponseHandler(new ObjectMapper().findAndRegisterModules(), ApplicationClockFactory.systemClock()), ApplicationClockFactory.systemClock(),
            new jp.xoki.web.zunda.smarthomewebserverbackend.common.config.SecuritySettingProperties());

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void databaseFailureNeverFallsBackToUserAuthentication() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin", null, List.of()));
        when(service.authenticate("secret")).thenThrow(new DataAccessResourceFailureException("unavailable"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request(), response, chain);
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("API_KEY_UNAVAILABLE").doesNotContain("secret");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void invalidKeyRejectsWithoutInvokingController() throws Exception {
        when(service.authenticate("secret")).thenThrow(new BadCredentialsException("Invalid API key"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request(), response, chain);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void successfulAuthenticationUsesOnlyDedicatedIdentityAndScopes() throws Exception {
        UUID id = UUID.randomUUID();
        ExternalApiKeyDto key = new ExternalApiKeyDto(id, "test", ExternalApiConstants.API_KEY_PREFIX + "_prefix", Set.of("inventory:read"), "ACTIVE",
                null, null, null, "admin", null, null);
        when(service.authenticate("secret")).thenReturn(key);
        when(service.consume(key)).thenReturn(Optional.of(1));
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(), response, new MockFilterChain());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getName()).isEqualTo("external:" + id);
        assertThat(authentication.getCredentials()).isNull();
        assertThat(authentication.getAuthorities()).extracting("authority")
                .containsExactlyInAnyOrder("ROLE_EXTERNAL", "SCOPE_inventory:read");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("999");
    }

    @Test
    void webAgentIotAndPreflightRequestsAreNotAuthenticatedByThisFilter() throws Exception {
        for (String path : List.of("/api/notifications", "/api/v1/agent/me", "/api/v1/iot/telemetry")) {
            filter.doFilter(new MockHttpServletRequest("GET", path), new MockHttpServletResponse(), new MockFilterChain());
        }
        filter.doFilter(new MockHttpServletRequest("OPTIONS", "/api/v1/external/inventory/stocks"),
                new MockHttpServletResponse(), new MockFilterChain());
        verifyNoInteractions(service);
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/external/inventory/stocks");
        request.addHeader("X-API-Key", "secret");
        return request;
    }
}
