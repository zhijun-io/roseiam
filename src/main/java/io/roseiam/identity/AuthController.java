package io.roseiam.identity;

import io.roseiam.platformsetting.MailDeliveryService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final IdentityService identity;
    private final MailDeliveryService mail;

    public AuthController(IdentityService identity, MailDeliveryService mail) {
        this.identity = identity;
        this.mail = mail;
    }

    @PostMapping("/login")
    public AuthDtos.LoginResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return identity.login(request);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = "Authorization", required = false) String token) {
        identity.logout(token);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password-recoveries")
    public ResponseEntity<Void> requestRecovery(@Valid @RequestBody AuthDtos.PasswordRecoveryRequest request) {
        AuthDtos.RecoveryDelivery delivery = identity.requestPasswordRecovery(request.email());
        if (delivery != null) {
            try {
                mail.sendPasswordRecovery(delivery.email(), delivery.token());
            } catch (RuntimeException exception) {
                logger.error("Could not send password recovery email");
            }
        }
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/password-recoveries/completion")
    public ResponseEntity<Void> completeRecovery(@Valid @RequestBody AuthDtos.PasswordRecoveryCompletion request) {
        identity.completePasswordRecovery(request.token(), request.password());
        return ResponseEntity.noContent().build();
    }
}
