package io.roseiam.user;

import io.roseiam.config.ApiException;
import io.roseiam.identity.AuthDtos;
import io.roseiam.identity.UserPrincipal;
import io.roseiam.platformsetting.MailDeliveryService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class UserController {
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    private final UserService users;
    private final UserSettingService settings;
    private final MailDeliveryService mail;

    public UserController(UserService users, UserSettingService settings, MailDeliveryService mail) {
        this.users = users;
        this.settings = settings;
        this.mail = mail;
    }

    @PostMapping("/user-invitations")
    public ResponseEntity<AuthDtos.UserCreateResponse> invite(
            @Valid @RequestBody AuthDtos.UserCreateRequest request, Authentication auth) {
        var created = users.invite(request, principal(auth));
        try {
            mail.sendInvitation(created.delivery().email(), created.delivery().token());
        } catch (RuntimeException ex) {
            logger.error("Could not send user invitation email");
            throw new io.roseiam.config.ApiException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "mail_delivery_failed",
                    "Invitation email could not be sent");
        }
        return ResponseEntity.status(201).body(created.response());
    }

    @PostMapping("/user-invitations/acceptance")
    public ResponseEntity<Void> accept(@Valid @RequestBody AuthDtos.UserAcceptInvitationRequest request) {
        users.accept(request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/users")
    public AuthDtos.UserPageResponse list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        return users.list(page, size, status);
    }

    @GetMapping("/users/{id}")
    public AuthDtos.UserResponse get(@PathVariable long id) {
        return users.getUser(id);
    }

    @PatchMapping("/users/{id}")
    public AuthDtos.UserResponse update(
            @PathVariable long id, @Valid @RequestBody AuthDtos.UserRequest request, Authentication auth) {
        return users.updateName(id, request.name(), principal(auth));
    }

    @PostMapping("/users/{id}/disable")
    public AuthDtos.UserResponse disable(@PathVariable long id, Authentication auth) {
        users.disable(id, principal(auth));
        return users.listOne(id);
    }

    @PostMapping("/users/{id}/enable")
    public AuthDtos.UserResponse enable(@PathVariable long id, Authentication auth) {
        users.enable(id, principal(auth));
        return users.listOne(id);
    }

    @GetMapping("/me")
    public AuthDtos.UserMeResponse me(Authentication auth) {
        var user = users.listOne(principal(auth).userId());
        return new AuthDtos.UserMeResponse(user.id(), user.email(), user.emailVerifiedAt(), user.name(), user.status());
    }

    @PatchMapping("/me")
    public AuthDtos.UserMeResponse updateMe(@Valid @RequestBody AuthDtos.UserRequest request, Authentication auth) {
        var user = users.updateOwnName(principal(auth).userId(), request.name(), principal(auth));
        return new AuthDtos.UserMeResponse(user.id(), user.email(), user.emailVerifiedAt(), user.name(), user.status());
    }

    @GetMapping("/me/settings")
    public java.util.Map<String, Object> getSettings(Authentication auth) {
        return settings.get(principal(auth));
    }

    @org.springframework.web.bind.annotation.PutMapping("/me/settings")
    public java.util.Map<String, Object> putSettings(
            @RequestBody java.util.Map<String, Object> body, Authentication auth) {
        return settings.replace(principal(auth), body);
    }

    private static UserPrincipal principal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal value))
            throw ApiException.unauthorized();
        return value;
    }
}
