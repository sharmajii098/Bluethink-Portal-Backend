package EmployeeManagementSystem.security.jwt;

import EmployeeManagementSystem.entity.UserSession;
import EmployeeManagementSystem.entity.RegisterEmployee;
import EmployeeManagementSystem.repository.RegisterEmployeeRepository;
import EmployeeManagementSystem.repository.UserSessionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtUtil jwtUtil;
    private final UserSessionRepository userSessionRepository;
    private final RegisterEmployeeRepository registerEmployeeRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        if (isPublicPath(request.getServletPath())) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            authenticate(request);
        } catch (Exception e) {
            SecurityContextHolder.clearContext();
            log.warn("JWT authentication failed ({})", e.getClass().getSimpleName());
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request) {
        String token = extractToken(request);
        if (token == null) {
            SecurityContextHolder.clearContext();
            return;
        }

        Optional<UserSession> optionalSession =
                userSessionRepository.findByJwtTokenAndIsActiveTrue(token);
        if (optionalSession.isEmpty()) {
            SecurityContextHolder.clearContext();
            return;
        }

        UserSession session = optionalSession.get();
        String username = jwtUtil.extractUsername(token);
        String tokenRole = normalizeRole(jwtUtil.extractRole(token));
        if (!jwtUtil.validateToken(token, username)
                || username == null
                || !username.equals(session.getEmployeeId())) {
            revoke(session);
            return;
        }

        RegisterEmployee account = registerEmployeeRepository.findByUserId(username);
        if (!isActive(account) || !tokenRole.equals(normalizeRole(account.getRole()))) {
            revoke(session);
            return;
        }

        if (session.getExpiresAt() != null && session.getExpiresAt().isBefore(LocalDateTime.now())) {
            revoke(session);
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            var authentication = new UsernamePasswordAuthenticationToken(
                    username, null, List.of(new SimpleGrantedAuthority(tokenRole)));
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
    }

    private boolean isActive(RegisterEmployee account) {
        if (account == null) {
            return false;
        }
        return account.getEmployee() == null
                || account.getEmployee().getEmployeeProfile() == null
                || account.getEmployee().getEmployeeProfile().getStatus() == null
                || "ACTIVE".equalsIgnoreCase(account.getEmployee().getEmployeeProfile().getStatus());
    }

    private String normalizeRole(String role) {
        if (role == null) {
            return "";
        }
        String normalized = role.trim().toUpperCase();
        return normalized.startsWith("ROLE_") ? normalized : "ROLE_" + normalized;
    }

    private void revoke(UserSession session) {
        session.setIsActive(false);
        userSessionRepository.save(session);
        SecurityContextHolder.clearContext();
    }

    // ================= PUBLIC PATHS =================

    private boolean isPublicPath(String path) {

        // Legacy MVC authentication endpoints remain public.
        // The React REST API is different: only the login endpoint is public.
        // Endpoints such as /api/auth/me, /sessions, /session-monitor,
        // /work-session/today and /signoff need this filter to populate
        // Spring Security's Authentication from the Bearer token.
        if (path.startsWith("/auth/")) {
            return true;
        }

        if (path.startsWith("/api/public/password-reset/") || path.startsWith("/api/public/invitations/")) {
            return true;
        }

        if ("/api/auth/login".equals(path)) {
            return true;
        }

        return path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/images/")
                || path.startsWith("/webjars/")
                || path.equals("/favicon.ico")
                || path.equals("/error")
                || path.startsWith("/h2-console");
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ")
                ? header.substring(7)
                : null;
    }
}