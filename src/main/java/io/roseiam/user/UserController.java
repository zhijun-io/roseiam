package io.roseiam.user;

import io.roseiam.config.ApiException;
import io.roseiam.shared.UserPrincipal;
import jakarta.validation.Valid;
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
    private final UserService users;
    private final UserSettingService settings;

    public UserController(UserService users, UserSettingService settings) {
        this.users = users;
        this.settings = settings;
    }

    @PostMapping("/user-invitations")
    public ResponseEntity<UserDtos.UserCreateResponse> invite(
            @Valid @RequestBody UserDtos.UserCreateRequest request, Authentication auth) {
        return ResponseEntity.status(201)
                .body(users.invite(request, principal(auth)).response());
    }

    @PostMapping("/user-invitations/acceptance")
    public ResponseEntity<Void> accept(@Valid @RequestBody UserDtos.UserAcceptInvitationRequest request) {
        users.accept(request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/users")
    public UserDtos.UserPageResponse list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        return users.list(page, size, status);
    }

    @GetMapping("/users/{id}")
    public UserDtos.UserResponse get(@PathVariable long id) {
        return users.getUser(id);
    }

    @PatchMapping("/users/{id}")
    public UserDtos.UserResponse update(
            @PathVariable long id, @Valid @RequestBody UserDtos.UserRequest request, Authentication auth) {
        return users.updateName(id, request.name(), principal(auth));
    }

    @PostMapping("/users/{id}/disable")
    public UserDtos.UserResponse disable(@PathVariable long id, Authentication auth) {
        users.disable(id, principal(auth));
        return users.listOne(id);
    }

    @PostMapping("/users/{id}/enable")
    public UserDtos.UserResponse enable(@PathVariable long id, Authentication auth) {
        users.enable(id, principal(auth));
        return users.listOne(id);
    }

    @GetMapping("/me")
    public UserDtos.UserMeResponse me(Authentication auth) {
        var user = users.listOne(principal(auth).userId());
        return new UserDtos.UserMeResponse(user.id(), user.email(), user.emailVerifiedAt(), user.name(), user.status());
    }

    @PatchMapping("/me")
    public UserDtos.UserMeResponse updateMe(@Valid @RequestBody UserDtos.UserRequest request, Authentication auth) {
        var user = users.updateOwnName(principal(auth).userId(), request.name(), principal(auth));
        return new UserDtos.UserMeResponse(user.id(), user.email(), user.emailVerifiedAt(), user.name(), user.status());
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
