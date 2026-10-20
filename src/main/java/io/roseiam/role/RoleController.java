package io.roseiam.role;

import io.roseiam.config.ApiException;
import io.roseiam.shared.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RoleController {
    private final RoleService roles;

    public RoleController(RoleService roles) {
        this.roles = roles;
    }

    @GetMapping("/permissions")
    public Object permissions() {
        return roles.permissions();
    }

    @GetMapping("/roles")
    public Object list() {
        return roles.roles();
    }

    @GetMapping("/roles/{id}")
    public Object get(@PathVariable long id) {
        return roles.get(id);
    }

    @PostMapping("/roles")
    public ResponseEntity<?> create(@Valid @RequestBody RoleRequest body, Authentication auth) {
        return ResponseEntity.status(201)
                .body(roles.create(
                        body.name(),
                        body.description(),
                        body.permissionIds() == null ? Set.of() : body.permissionIds(),
                        principal(auth)));
    }

    @PatchMapping("/roles/{id}")
    public Object patch(@PathVariable long id, @RequestBody Map<String, Object> body, Authentication auth) {
        return roles.update(id, body, principal(auth));
    }

    @DeleteMapping("/roles/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id, Authentication auth) {
        roles.delete(id, principal(auth));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/users/{userId}/roles/{roleId}")
    public ResponseEntity<Void> assign(@PathVariable long userId, @PathVariable long roleId, Authentication auth) {
        roles.assign(userId, roleId, principal(auth), true);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/users/{userId}/roles/{roleId}")
    public ResponseEntity<Void> revoke(@PathVariable long userId, @PathVariable long roleId, Authentication auth) {
        roles.assign(userId, roleId, principal(auth), false);
        return ResponseEntity.noContent().build();
    }

    private static UserPrincipal principal(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal p)) throw ApiException.unauthorized();
        return p;
    }

    public record RoleRequest(@NotBlank String name, String description, Set<Long> permissionIds) {}
}
