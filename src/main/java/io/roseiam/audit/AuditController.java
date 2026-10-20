package io.roseiam.audit;

import io.roseiam.config.ApiException;
import io.roseiam.shared.UserPrincipal;
import java.time.Instant;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-events")
public class AuditController {
    private final AuditQueryService audit;

    public AuditController(AuditQueryService audit) {
        this.audit = audit;
    }

    @GetMapping
    public Map<String, Object> page(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            Authentication authentication) {
        return audit.page(
                page, size, actorUserId, action, targetType, targetId, result, from, to, principal(authentication));
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable long id, Authentication authentication) {
        return audit.get(id, principal(authentication));
    }

    private static UserPrincipal principal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal user)) {
            throw ApiException.unauthorized();
        }
        return user;
    }
}
