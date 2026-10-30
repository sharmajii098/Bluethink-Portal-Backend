package EmployeeManagementSystem.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/**
 * Explicit no-store for every /api/ai response, including authentication failures,
 * role denials, server exceptions and throttling. Runs before JWT authentication.
 * Does not alter response bodies, status codes or application authorization.
 */
@Component
public class AiNoStoreFilter extends OncePerRequestFilter {
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        String path = uri.substring(context == null ? 0 : context.length());
        return !(path.equals("/api/ai") || path.startsWith("/api/ai/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
        chain.doFilter(request, response);
    }
}
