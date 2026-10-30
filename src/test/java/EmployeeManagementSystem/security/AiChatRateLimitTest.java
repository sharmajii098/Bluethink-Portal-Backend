package EmployeeManagementSystem.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiChatRateLimitTest {
    @Test void aiChatIsRateLimitedPerClientWithoutImpactingOtherApis() throws Exception {
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        ChatRateLimitFilter limiter = new ChatRateLimitFilter(provider, false);
        assertFalse(limiter.shouldNotFilter(new MockHttpServletRequest("POST", "/api/ai/chat")));
        assertTrue(limiter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/employees/my")));
        for (int i = 0; i < 30; i++) {
            var request = new MockHttpServletRequest("POST", "/api/ai/chat");
            request.setRemoteAddr("198.51.100.45");
            var response = new MockHttpServletResponse();
            limiter.doFilter(request, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(200));
            assertEquals(200, response.getStatus());
        }
        var request = new MockHttpServletRequest("POST", "/api/ai/chat");
        request.setRemoteAddr("198.51.100.45");
        var response = new MockHttpServletResponse();
        limiter.doFilter(request, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(200));
        assertEquals(429, response.getStatus());
        assertEquals("60", response.getHeader("Retry-After"));
        assertTrue(response.getHeader("Cache-Control").contains("no-store"));
    }
}
