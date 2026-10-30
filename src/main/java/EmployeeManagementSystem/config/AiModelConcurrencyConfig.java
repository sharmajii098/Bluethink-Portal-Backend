package EmployeeManagementSystem.config;

import EmployeeManagementSystem.service.AiModelConcurrencyGate;
import EmployeeManagementSystem.service.AiModelCircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiModelConcurrencyConfig {
    @Bean
    public AiModelCircuitBreaker aiModelCircuitBreaker(
            @Value("${hrms.ai.circuit-failure-threshold:3}") int failures,
            @Value("${hrms.ai.circuit-reset-seconds:30}") int seconds) {
        return new AiModelCircuitBreaker(failures, seconds);
    }

    @Bean
    public AiModelConcurrencyGate aiModelConcurrencyGate(
            @Value("${hrms.ai.max-concurrent-model-calls:3}") int maxConcurrent) {
        return new AiModelConcurrencyGate(maxConcurrent);
    }
}
