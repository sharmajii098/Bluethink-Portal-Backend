package EmployeeManagementSystem.security;
import jakarta.servlet.*; import jakarta.servlet.http.*; import org.springframework.stereotype.Component; import org.springframework.web.filter.OncePerRequestFilter; import java.io.IOException; import java.util.concurrent.*; import java.util.concurrent.atomic.AtomicInteger;
/** Small single-instance safety limiter. Use a shared Redis/API-gateway limiter for multi-replica production deployments. */
@Component public class ChatRateLimitFilter extends OncePerRequestFilter {
 private record Window(long start,AtomicInteger count){} private final ConcurrentHashMap<String,Window> windows=new ConcurrentHashMap<>(); private static final int LIMIT=90; private static final long WINDOW_MS=60_000;
 @Override protected boolean shouldNotFilter(HttpServletRequest r){return !r.getRequestURI().contains("/api/chat/");}
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException {String key=req.getRemoteAddr()+":"+req.getMethod();long now=System.currentTimeMillis();Window w=windows.compute(key,(k,old)->old==null||now-old.start>=WINDOW_MS?new Window(now,new AtomicInteger()):old);if(w.count.incrementAndGet()>LIMIT){res.setStatus(429);res.setHeader("Retry-After","60");res.setContentType("application/json");res.getWriter().write("{\"message\":\"Chat rate limit exceeded. Retry in one minute.\"}");return;}if(windows.size()>10000)windows.entrySet().removeIf(e->now-e.getValue().start>WINDOW_MS*2);chain.doFilter(req,res);}
}
