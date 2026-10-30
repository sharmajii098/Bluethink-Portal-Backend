package EmployeeManagementSystem.security.jwt;
import EmployeeManagementSystem.security.SessionFingerprint;

import EmployeeManagementSystem.entity.RegisterEmployee;
import EmployeeManagementSystem.entity.UserSession;
import EmployeeManagementSystem.repository.RegisterEmployeeRepository;
import EmployeeManagementSystem.repository.UserSessionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private final JwtUtil jwtUtil;
    private final UserSessionRepository userSessionRepository;
    private final RegisterEmployeeRepository registerEmployeeRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!isPublicPath(request.getServletPath())) {
            try {
                authenticate(request);
            } catch (org.springframework.dao.DataAccessException unavailable) {
                SecurityContextHolder.clearContext();
                response.setStatus(503);
                response.setContentType("application/json");
                response.getWriter().write("{\"success\":false,\"message\":\"Sign-in service temporarily unavailable\"}");
                return;
            } catch (Exception ex) {
                SecurityContextHolder.clearContext();
                // Do not log credentials, JWTs or claim values.
                log.debug("Bearer authentication failed: {}", ex.getClass().getSimpleName());
            }
        }
        // Keep downstream execution outside the authentication catch. A controller
        // failure must never cause the filter chain (and mutations) to run twice.
        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        String header = request.getHeader("Authorization");
        // Explicit bearer credentials only: ambient cookies are unsafe with CSRF disabled.
        if (header == null || !header.startsWith("Bearer ")) return;
        String token = header.substring(7).trim();
        if (token.isEmpty()) return;
        String username = jwtUtil.extractUsername(token);
        String role = normalizeRole(jwtUtil.extractRole(token));
        if (username == null || !List.of("ADMIN", "HR", "MANAGER", "EMPLOYEE").contains(role)
                || !jwtUtil.validateToken(token, username)) return;
        UserSession session = userSessionRepository.findByJwtTokenAndIsActiveTrue(SessionFingerprint.of(token)).orElse(null);
        if (session == null) return;
        RegisterEmployee account = registerEmployeeRepository.findByUserId(session.getEmployeeId());
        boolean inactive = account != null && account.getEmployee() != null
                && account.getEmployee().getEmployeeProfile() != null
                && account.getEmployee().getEmployeeProfile().getStatus() != null
                && !"ACTIVE".equalsIgnoreCase(account.getEmployee().getEmployeeProfile().getStatus());
        // Reject stale claims globally, including controllers that parse the JWT themselves.
        if (account == null || inactive || !username.equals(session.getEmployeeId())
                || !username.equals(account.getUserId()) || !role.equals(normalizeRole(account.getRole()))
                || session.getLogoutAt() != null || session.getExpiresAt() == null
                || !session.getExpiresAt().isAfter(LocalDateTime.now())) {
            session.setIsActive(false);
            userSessionRepository.save(session);
            return;
        }
        var authentication = new UsernamePasswordAuthenticationToken(username, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String normalizeRole(String role) {
        if (role == null) return "";
        String value = role.trim().toUpperCase(Locale.ROOT);
        return value.startsWith("ROLE_") ? value.substring(5) : value;
    }

    private boolean isPublicPath(String path) {
        return path.startsWith("/auth/") || path.startsWith("/api/public/password-reset/")
                || path.startsWith("/api/public/invitations/") || "/api/auth/login".equals(path)
                || "/favicon.ico".equals(path) || "/error".equals(path);
    }
}
