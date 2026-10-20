package io.roseiam.user;

import io.roseiam.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class BootstrapAdminRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);
    private final String email;
    private final UserService users;

    public BootstrapAdminRunner(AppProperties properties, UserService users) {
        this.email = properties.getBootstrapAdminEmail();
        this.users = users;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank()) return;
        UserDtos.CreatedInvitation invitation = users.createBootstrapInvitation(email);
        if (invitation == null) {
            log.info("Bootstrap administrator already exists");
            return;
        }
        log.info("Bootstrap administrator invitation requested for {}", email);
    }
}
