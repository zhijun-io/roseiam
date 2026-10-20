package io.roseiam.user;

import io.roseiam.identity.AuthDtos;
import io.roseiam.platformsetting.MailDeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
    private final UserLifecycleService users;
    private final MailDeliveryService mail;

    public BootstrapAdminRunner(
            @Value("${app.bootstrap.admin-email:}") String email,
            UserLifecycleService users,
            MailDeliveryService mail) {
        this.email = email;
        this.users = users;
        this.mail = mail;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank()) return;
        AuthDtos.CreatedInvitation invitation = users.createBootstrapInvitation(email);
        if (invitation == null) {
            log.info("Bootstrap administrator already exists");
            return;
        }
        mail.sendInvitation(invitation.delivery().email(), invitation.delivery().token());
        log.info("Bootstrap administrator invitation sent to {}", email);
    }
}
