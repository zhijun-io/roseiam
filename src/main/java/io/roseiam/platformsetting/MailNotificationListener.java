package io.roseiam.platformsetting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class MailNotificationListener {
    private static final Logger logger = LoggerFactory.getLogger(MailNotificationListener.class);

    private final MailDeliveryService mail;

    public MailNotificationListener(MailDeliveryService mail) {
        this.mail = mail;
    }

    @Async("mailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendInvitation(InvitationMailRequest event) {
        try {
            mail.sendInvitation(event.recipient(), event.token());
        } catch (RuntimeException exception) {
            logger.error("Invitation email delivery failed; requestId={}", event.requestId(), exception);
        }
    }

    @Async("mailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendPasswordReset(PasswordResetRequest event) {
        try {
            mail.sendPasswordReset(event.recipient(), event.token());
        } catch (RuntimeException exception) {
            logger.error("Password reset email delivery failed; requestId={}", event.requestId(), exception);
        }
    }
}
