package EmployeeManagementSystem.controller;

import java.sql.Connection;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Core readiness only. Optional AI/email/calendar outages do not block the portal. */
@RestController
@RequestMapping("/api/health")
public class ReadinessController {
    private final DataSource dataSource;
    private final ObjectProvider<StringRedisTemplate> redis;
    private final boolean redisRequired;
    private final ApplicationAvailability availability;
    public ReadinessController(DataSource dataSource, ObjectProvider<StringRedisTemplate> redis,
            @Value("${bluethink.chat.redis-enabled:false}") boolean redisRequired,
            ApplicationAvailability availability) {
        this.dataSource = dataSource;
        this.redis = redis;
        this.redisRequired = redisRequired;
        this.availability = availability;
    }
    @GetMapping("/ready")
    public ResponseEntity<Map<String,String>> ready() {
        if (availability.getReadinessState() != ReadinessState.ACCEPTING_TRAFFIC) return down();
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(2)) return down();
            if (redisRequired) {
                StringRedisTemplate template = redis.getIfAvailable();
                if (template == null || template.getConnectionFactory() == null) return down();
                try (var redisConnection = template.getConnectionFactory().getConnection()) {
                    if (!"PONG".equals(redisConnection.ping())) return down();
                }
            }
            return ResponseEntity.ok(Map.of("status", "UP"));
        } catch (Exception ignored) { return down(); }
    }
    private ResponseEntity<Map<String,String>> down() {
        return ResponseEntity.status(503).body(Map.of("status", "DOWN"));
    }
}
