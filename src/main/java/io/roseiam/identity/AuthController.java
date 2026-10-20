package io.roseiam.identity;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final IdentityService identity;

    public AuthController(IdentityService identity) {
        this.identity = identity;
    }

    @PostMapping("/password-resets")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody AuthDtos.PasswordResetRequest request) {
        identity.requestPasswordReset(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/password-resets/completion")
    public ResponseEntity<Void> completePasswordReset(@Valid @RequestBody AuthDtos.PasswordResetCompletion request) {
        identity.completePasswordReset(request.token(), request.password());
        return ResponseEntity.noContent().build();
    }
}
