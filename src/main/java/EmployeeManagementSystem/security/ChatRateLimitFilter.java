package EmployeeManagementSystem.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Per-client chat rate limiting. Redis mode is shared by all replicas and fails closed if unavailable. */
@Component
public class ChatRateLimitFilter extends OncePerRequestFilter {
 private record Window(long start, AtomicInteger count) {}
 private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
 private static final int LIMIT = 90;
 private static final long WINDOW_MS = 60_000L;
 private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>(
  "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('PEXPIRE',KEYS[1],ARGV[1]); end; return n", Long.class);
 private final ObjectProvider<StringRedisTemplate> redisProvider;
 private final boolean redisEnabled;
 public ChatRateLimitFilter(ObjectProvider<StringRedisTemplate> redisProvider,
       @Value("${bluethink.chat.redis-enabled:false}") boolean redisEnabled) {
  this.redisProvider=redisProvider; this.redisEnabled=redisEnabled;
 }
 @Override protected boolean shouldNotFilter(HttpServletRequest r) {
  String path=path(r);
  return !(path.equals("/api/auth/login") || path.startsWith("/auth/") || path.startsWith("/api/public/") || path.startsWith("/api/ai/knowledge") || path.equals("/api/ai/chat") || path.startsWith("/api/ai/chat/") || path.equals("/api/chat") || path.startsWith("/api/chat/") || path.equals("/api/team-chat") || path.startsWith("/api/team-chat/") ||
      path.startsWith("/api/calendar-integrations") || path.startsWith("/api/meetings") || path.startsWith("/api/letter-requests") || path.startsWith("/api/hr-letter-templates") || path.startsWith("/api/payroll-reconciliations") || path.startsWith("/api/ai/actions/"));
 }
 private static String path(HttpServletRequest req) {
  String uri=req.getRequestURI();
  String context=req.getContextPath();
  return uri.substring(context==null?0:context.length());
 }
 private String clientKey(HttpServletRequest req) {
  // Do not trust X-Forwarded-For sent by clients. Proxy forwarding must be explicitly configured at the edge.
  org.springframework.security.core.Authentication a=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
  String id=a!=null&&a.isAuthenticated()&&!(a instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)?"user:"+a.getName():"ip:"+req.getRemoteAddr();
  String uri=path(req);
  String group=uri.equals("/api/ai/chat")||uri.startsWith("/api/ai/chat/")?"ai-chat":
      uri.startsWith("/api/chat")||uri.startsWith("/api/team-chat")?"chat":"workflow";
  try {
   byte[] hash=MessageDigest.getInstance("SHA-256").digest(id.getBytes(StandardCharsets.UTF_8));
   return HexFormat.of().formatHex(hash,0,16)+":"+group+":"+("GET".equals(req.getMethod())?"read":"write");
  } catch(Exception e) {throw new IllegalStateException("SHA-256 unavailable",e);}
 }
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
  String key=clientKey(req);
  long count;
  if(redisEnabled) {
   try {
    StringRedisTemplate redis=redisProvider.getIfAvailable();
    if(redis==null) throw new IllegalStateException("Redis template unavailable");
    Long value=redis.execute(SCRIPT, java.util.List.of("bluethink:chat:rl:"+key),String.valueOf(WINDOW_MS));
    if(value==null) throw new IllegalStateException("Redis script returned null");
    count=value;
   } catch(Exception e) {
    res.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,"Requests temporarily unavailable; distributed rate limiter is offline");
    return;
   }
  } else {
   long now=System.currentTimeMillis();
   // Bound memory growth during a flood of unique identities.
   if(windows.size()>10_000) windows.entrySet().removeIf(e->now-e.getValue().start>=WINDOW_MS);
   if(windows.size()>20_000 && !windows.containsKey(key)) {
    res.sendError(503,"Request capacity reached"); return;
   }
   Window window=windows.compute(key,(k,old)->old==null||now-old.start>=WINDOW_MS?new Window(now,new AtomicInteger()):old);
   count=window.count.incrementAndGet();
  }
  // LLM calls are more resource-intensive than ordinary workflow endpoints.
  String uri = path(req);
  int maxRequests = (uri.equals("/api/auth/login") || uri.startsWith("/auth/") || uri.startsWith("/api/public/")) ? 20 : "GET".equals(req.getMethod()) ? 240
      : (uri.equals("/api/ai/chat") || uri.startsWith("/api/ai/chat/")) ? 30 : LIMIT;
  if(count>maxRequests) {
   res.setStatus(429);res.setHeader("Retry-After","60");res.setHeader("Cache-Control","no-store");
   res.setContentType("application/json");res.getWriter().write("{\"message\":\"Request rate limit exceeded. Retry shortly.\"}");return;
  }
  chain.doFilter(req,res);
 }
}
