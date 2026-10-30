package EmployeeManagementSystem.security;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.concurrent.ConcurrentHashMap;
/** Per-account fixed window protects against distributed password guessing. Redis is mandatory in prod. */
@Service public class LoginAttemptGuard {
 private final ObjectProvider<StringRedisTemplate> redis; private final boolean distributed;
 private record Window(long start,int count) {}
 private final ConcurrentHashMap<String,Window> windows=new ConcurrentHashMap<>();
 private static final long WINDOW=300_000;
 private static final DefaultRedisScript<Long> SCRIPT=new DefaultRedisScript<>("local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('PEXPIRE',KEYS[1],ARGV[1]); end; return n",Long.class);
 public LoginAttemptGuard(ObjectProvider<StringRedisTemplate> redis,@Value("${bluethink.chat.redis-enabled:false}") boolean distributed){this.redis=redis;this.distributed=distributed;}
 public void check(String account){
  String key=SessionFingerprint.of(account==null||account.isBlank()?"unknown":account.trim().toLowerCase(java.util.Locale.ROOT)); long count;
  if(distributed){try{StringRedisTemplate client=redis.getIfAvailable();if(client==null)throw new IllegalStateException();Long n=client.execute(SCRIPT,java.util.List.of("bluethink:login:"+key),Long.toString(WINDOW));if(n==null)throw new IllegalStateException();count=n;}catch(Exception e){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Sign-in protection unavailable. Try again later.");}}
  else{long now=System.currentTimeMillis();if(windows.size()>10000)windows.entrySet().removeIf(e->now-e.getValue().start()>=WINDOW);if(windows.size()>20000&&!windows.containsKey(key))throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Sign-in capacity reached");count=windows.compute(key,(k,w)->w==null||now-w.start()>=WINDOW?new Window(now,1):new Window(w.start(),w.count()+1)).count();}
  if(count>10)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Too many sign-in attempts. Try again in five minutes.");
 }
}
