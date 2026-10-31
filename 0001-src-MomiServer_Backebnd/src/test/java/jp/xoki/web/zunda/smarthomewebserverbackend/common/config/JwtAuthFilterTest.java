package jp.xoki.web.zunda.smarthomewebserverbackend.common.config;

import io.jsonwebtoken.MalformedJwtException;
import jp.xoki.web.zunda.smarthomewebserverbackend.auth.service.AuthSessionService;
import jp.xoki.web.zunda.smarthomewebserverbackend.auth.service.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

	@Mock
	private JwtService jwtService;

	@Mock
	private AuthSessionService authSessionService;

	@Mock
	private UserDetailsService userDetailsService;

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void doFilterSkipsAuthenticationWhenAuthorizationHeaderIsMissing() throws Exception {
		JwtAuthFilter filter = filter();
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/master/user/list");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		assertThat(chain.getRequest()).isSameAs(request);
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		verifyNoInteractions(jwtService, authSessionService, userDetailsService);
	}

	@Test
	void doFilterSkipsAuthenticationWhenAuthorizationHeaderIsNotBearer() throws Exception {
		JwtAuthFilter filter = filter();
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/master/user/list");
		request.addHeader("Authorization", "Basic token");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		assertThat(chain.getRequest()).isSameAs(request);
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		verifyNoInteractions(jwtService, authSessionService, userDetailsService);
	}

	@Test
	void doFilterClearsContextAndContinuesWhenTokenCannotBeParsed() throws Exception {
		JwtAuthFilter filter = filter();
		SecurityContextHolder.getContext().setAuthentication(existingAuthentication());
		MockHttpServletRequest request = bearerRequest("broken-token");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();
		when(jwtService.extractUsername("broken-token"))
				.thenThrow(new MalformedJwtException("broken"));

		filter.doFilter(request, response, chain);

		assertThat(chain.getRequest()).isSameAs(request);
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		verify(jwtService).extractUsername("broken-token");
		verify(jwtService, never()).extractSessionId("broken-token");
		verifyNoInteractions(authSessionService, userDetailsService);
	}

	@Test
	void doFilterContinuesWithoutAuthenticationWhenUserIsNotFound() throws Exception {
		JwtAuthFilter filter = filter();
		MockHttpServletRequest request = bearerRequest("access-token");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();
		when(jwtService.extractUsername("access-token")).thenReturn("missing@example.com");
		when(jwtService.extractSessionId("access-token")).thenReturn("session-1");
		when(userDetailsService.loadUserByUsername("missing@example.com"))
				.thenThrow(new UsernameNotFoundException("missing"));

		filter.doFilter(request, response, chain);

		assertThat(chain.getRequest()).isSameAs(request);
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		verify(jwtService, never()).isValidAccessToken(eq("access-token"), any(UserDetails.class));
		verifyNoInteractions(authSessionService);
	}

	@Test
	void doFilterContinuesWithoutAuthenticationWhenSessionIsInactive() throws Exception {
		JwtAuthFilter filter = filter();
		UserDetails user = user("user@example.com");
		MockHttpServletRequest request = bearerRequest("access-token");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();
		when(jwtService.extractUsername("access-token")).thenReturn(user.getUsername());
		when(jwtService.extractSessionId("access-token")).thenReturn("session-1");
		when(userDetailsService.loadUserByUsername(user.getUsername())).thenReturn(user);
		when(jwtService.isValidAccessToken("access-token", user)).thenReturn(true);
		when(authSessionService.isActive("session-1", user.getUsername())).thenReturn(false);

		filter.doFilter(request, response, chain);

		assertThat(chain.getRequest()).isSameAs(request);
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	@Test
	void doFilterSetsAuthenticationWhenAccessTokenAndSessionAreValid() throws Exception {
		JwtAuthFilter filter = filter();
		UserDetails user = user("user@example.com");
		MockHttpServletRequest request = bearerRequest("access-token");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();
		when(jwtService.extractUsername("access-token")).thenReturn(user.getUsername());
		when(jwtService.extractSessionId("access-token")).thenReturn("session-1");
		when(userDetailsService.loadUserByUsername(user.getUsername())).thenReturn(user);
		when(jwtService.isValidAccessToken("access-token", user)).thenReturn(true);
		when(authSessionService.isActive("session-1", user.getUsername())).thenReturn(true);

		filter.doFilter(request, response, chain);

		assertThat(chain.getRequest()).isSameAs(request);
		assertThat(SecurityContextHolder.getContext().getAuthentication())
				.isInstanceOf(UsernamePasswordAuthenticationToken.class);
		assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
				.isEqualTo(user.getUsername());
		assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
				.extracting("authority")
				.containsExactly("ROLE_USER");
	}

	@Test
	void doFilterRevokesSessionAndTreatsBannedUserAsUnauthenticated() throws Exception {
		JwtAuthFilter filter = filter();
		UserDetails user = new User("banned@example.com", "password",
				List.of(new SimpleGrantedAuthority("ROLE_BAN_USER")));
		MockHttpServletRequest request = bearerRequest("access-token");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();
		when(jwtService.extractUsername("access-token")).thenReturn(user.getUsername());
		when(jwtService.extractSessionId("access-token")).thenReturn("session-1");
		when(userDetailsService.loadUserByUsername(user.getUsername())).thenReturn(user);

		filter.doFilter(request, response, chain);

		assertThat(chain.getRequest()).isSameAs(request);
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		verify(authSessionService).revoke("session-1");
		verify(jwtService, never()).isValidAccessToken(eq("access-token"), any(UserDetails.class));
	}

	@Test
	void doFilterDoesNotOverwriteExistingAuthentication() throws Exception {
		JwtAuthFilter filter = filter();
		UsernamePasswordAuthenticationToken existingAuthentication = existingAuthentication();
		SecurityContextHolder.getContext().setAuthentication(existingAuthentication);
		MockHttpServletRequest request = bearerRequest("access-token");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();
		when(jwtService.extractUsername("access-token")).thenReturn("user@example.com");
		when(jwtService.extractSessionId("access-token")).thenReturn("session-1");

		filter.doFilter(request, response, chain);

		assertThat(chain.getRequest()).isSameAs(request);
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existingAuthentication);
		verifyNoInteractions(authSessionService, userDetailsService);
	}

	private JwtAuthFilter filter() {
		return new JwtAuthFilter(jwtService, authSessionService, userDetailsService);
	}

	private MockHttpServletRequest bearerRequest(String token) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/master/user/list");
		request.addHeader("Authorization", "Bearer " + token);
		return request;
	}

	private UserDetails user(String username) {
		return new User(username, "password", List.of(new SimpleGrantedAuthority("ROLE_USER")));
	}

	private UsernamePasswordAuthenticationToken existingAuthentication() {
		return new UsernamePasswordAuthenticationToken(
				"existing@example.com",
				null,
				List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
		);
	}

}
