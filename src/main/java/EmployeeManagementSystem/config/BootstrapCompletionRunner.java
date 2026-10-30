package EmployeeManagementSystem.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** An explicit one-off, unexposed provisioning container exits after all account work commits. */
@Component @Profile("bootstrap") @Order(100)
public class BootstrapCompletionRunner implements ApplicationRunner {
    private final ConfigurableApplicationContext context;
    public BootstrapCompletionRunner(ConfigurableApplicationContext context) { this.context = context; }
    @Override public void run(ApplicationArguments args) {
        int status = SpringApplication.exit(context, () -> 0);
        System.exit(status);
    }
}
