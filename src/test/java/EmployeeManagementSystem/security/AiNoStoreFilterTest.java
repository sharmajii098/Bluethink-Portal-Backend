package EmployeeManagementSystem.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;

class AiNoStoreFilterTest {
    private final AiNoStoreFilter filter = new AiNoStoreFilter();

    @Test void successfulAiRepliesAreNoStore() throws Exception {
        var req = new MockHttpServletRequest("POST", "/api/ai/chat");
        var res = new MockHttpServletResponse();
        filter.doFilter(req, res, (r, s) -> ((MockHttpServletResponse) s).setStatus(200));
        assertTrue(res.getHeader("Cache-Control").contains("no-store"));
        assertEquals("no-cache", res.getHeader("Pragma"));
        assertEquals(200, res.getStatus());
    }

    @Test void errorResponsesRemainNoStore() throws Exception {
        for (int status : new int[] {400, 401, 403, 429, 500}) {
            var req = new MockHttpServletRequest("GET", "/api/ai/actions/monitor");
            var res = new MockHttpServletResponse();
            filter.doFilter(req, res, (r, s) -> ((MockHttpServletResponse) s).setStatus(status));
            assertEquals(status, res.getStatus());
            assertTrue(res.getHeader("Cache-Control").contains("no-store"));
        }
    }

    @Test void contextPathIsHandledSafely() throws Exception {
        var req = new MockHttpServletRequest("GET", "/portal/api/ai/actions/status");
        req.setContextPath("/portal");
        var res = new MockHttpServletResponse();
        filter.doFilter(req, res, (r, s) -> ((MockHttpServletResponse) s).setStatus(403));
        assertTrue(res.getHeader("Cache-Control").contains("no-store"));
    }

    @Test void unrelatedRoutesDoNotReceiveAiSpecificHeaders() throws Exception {
        var req = new MockHttpServletRequest("GET", "/api/employees/my");
        var res = new MockHttpServletResponse();
        filter.doFilter(req, res, (r, s) -> ((MockHttpServletResponse) s).setStatus(200));
        assertNull(res.getHeader("Cache-Control"));
    }
}
