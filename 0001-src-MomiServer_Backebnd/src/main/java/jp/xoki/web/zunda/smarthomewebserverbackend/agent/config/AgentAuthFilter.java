package jp.xoki.web.zunda.smarthomewebserverbackend.agent.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.model.AgentPrincipal;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.repository.AgentAuthRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.service.AgentSecrets;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.SecurityErrorResponseHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import static java.util.concurrent.TimeUnit.MINUTES;
import static jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants.AgentAuthFilterConstants.BEARER_PREFIX;
import static jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants.AgentAuthFilterConstants.BOOTSTRAP_PATHS;
import static jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants.AgentAuthFilterConstants.MAX_AUTHORIZATION_LENGTH;
import java.time.Clock;
import org.springframework.security.core.Authentication;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants.AgentMessageConstants;

/** Agent BearerのみをAgent名前空間で認証する。ユーザー権限への流用を防止する。 */
@Component
@RequiredArgsConstructor
public class AgentAuthFilter extends OncePerRequestFilter {
    private final AgentAuthRepository repository;
    private final AgentAuthProperties properties;
    private final SecurityErrorResponseHandler errors;

    private final Clock clock;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getServletPath();
        // MockMvc may not populate servletPath; requestURI is stripped of the context path.
        if (path.isEmpty()) {
            path = request.getRequestURI().substring(request.getContextPath().length());
        }
        if (!path.startsWith("/api/v1/agent/")) {
            chain.doFilter(request, response);
            return;
        }
        response.setHeader("Cache-Control", "no-store");
        if ("POST".equals(request.getMethod()) && BOOTSTRAP_PATHS.contains(path)) {
            long minute = clock.millis() / MINUTES.toMillis(1);
            // Do not trust a caller-supplied X-Forwarded-For header.
            int count = repository.countRequest(AgentSecrets.hash(request.getRemoteAddr()), minute);
            if (count > properties.getRequestsPerMinute()) {
                response.setHeader("Retry-After", "60");
                errors.write(request, response, HttpStatus.TOO_MANY_REQUESTS.value(),
                        "Too Many Requests", "AGENT_RATE_LIMITED",
                        AgentMessageConstants.RATE_LIMITED);
                return;
            }
            chain.doFilter(request, response);
            return;
        }
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ma_") && header.length() <= MAX_AUTHORIZATION_LENGTH) {
            AgentPrincipal principal = repository.authenticate(AgentSecrets.hash(header.substring(BEARER_PREFIX.length())));
            if (principal != null) {
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_AGENT")));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication.getPrincipal()
                instanceof AgentPrincipal)) {
            errors.write(request, response, HttpStatus.FORBIDDEN.value(), "Forbidden", "ACCESS_DENIED",
                    AgentMessageConstants.CREDENTIALS_REQUIRED);
            return;
        }
        chain.doFilter(request, response);
    }
}
