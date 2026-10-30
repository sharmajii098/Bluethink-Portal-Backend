package EmployeeManagementSystem.security.jwt;

import EmployeeManagementSystem.entity.RegisterEmployee;
import EmployeeManagementSystem.entity.UserSession;
import EmployeeManagementSystem.repository.RegisterEmployeeRepository;
import EmployeeManagementSystem.repository.UserSessionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {
    private final JwtUtil jwt = mock(JwtUtil.class);
    private final UserSessionRepository sessions = mock(UserSessionRepository.class);
    private final RegisterEmployeeRepository accounts = mock(RegisterEmployeeRepository.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwt, sessions, accounts);
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private MockHttpServletRequest request() {
        var request = new MockHttpServletRequest("POST", "/api/employees");
        request.setServletPath("/api/employees");
        return request;
    }
    private UserSession session(String claimRole, String assignedRole) {
        when(jwt.extractUsername("token")).thenReturn("EMP0001");
        when(jwt.extractRole("token")).thenReturn(claimRole);
        when(jwt.validateToken("token", "EMP0001")).thenReturn(true);
        var session = new UserSession(); session.setEmployeeId("EMP0001");
        session.setExpiresAt(LocalDateTime.now().plusHours(1)); session.setIsActive(true);
        var account = new RegisterEmployee(); account.setUserId("EMP0001"); account.setRole(assignedRole);
        when(sessions.findByJwtTokenAndIsActiveTrue(EmployeeManagementSystem.security.SessionFingerprint.of("token"))).thenReturn(Optional.of(session));
        when(accounts.findByUserId("EMP0001")).thenReturn(account);
        return session;
    }
    @Test void databaseOutageReturns503WithoutInvalidatingCredentials() throws Exception {
        session("EMPLOYEE", "EMPLOYEE");
        when(sessions.findByJwtTokenAndIsActiveTrue(anyString()))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("unavailable"));
        var request = request(); request.addHeader("Authorization", "Bearer token");
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        assertEquals(503, response.getStatus());
        assertFalse(response.getContentAsString().contains("token"));
        verifyNoInteractions(chain);
        verify(sessions, never()).save(any());
    }
    @Test void staleElevatedRoleIsRejectedAndSessionRevoked() throws Exception {
        var saved = session("HR", "EMPLOYEE");
        var request = request(); request.addHeader("Authorization", "Bearer token");
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertFalse(saved.getIsActive()); verify(sessions).save(saved);
        verify(chain, times(1)).doFilter(any(), any());
    }
    @Test void currentRoleAuthenticates() throws Exception {
        session("MANAGER", "ROLE_MANAGER");
        var request = request(); request.addHeader("Authorization", "Bearer token");
        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));
        assertEquals("EMP0001", SecurityContextHolder.getContext().getAuthentication().getName());
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER")));
    }
    @Test void downstreamExceptionDoesNotExecuteRequestTwice() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        doThrow(new ServletException("downstream failure")).when(chain).doFilter(any(), any());
        assertThrows(ServletException.class, () -> filter.doFilter(request(), new MockHttpServletResponse(), chain));
        verify(chain, times(1)).doFilter(any(), any());
    }
    @Test void ambientCookieCannotAuthenticateStatelessApi() throws Exception {
        var request = request(); request.setCookies(new Cookie("jwtToken", "token"));
        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(jwt, sessions, accounts);
    }
    @Test void expiredDatabaseSessionCannotAuthenticate() throws Exception {
        var saved = session("EMPLOYEE", "EMPLOYEE"); saved.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        var request = request(); request.addHeader("Authorization", "Bearer token");
        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertFalse(saved.getIsActive());
    }
}
