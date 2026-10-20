package io.roseiam.audit;

import io.roseiam.shared.UserPrincipal;
import java.util.List;
import java.util.Map;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class AuditAspect {
    private final AuditService audit;

    public AuditAspect(AuditService audit) {
        this.audit = audit;
    }

    @Around("@annotation(audited)")
    public Object recordSuccessfulOperation(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        Object result = joinPoint.proceed();
        Object[] arguments = joinPoint.getArgs();
        Long actorId = actorId(arguments, audited.actorParameter());
        String targetId = targetId(arguments, result, audited);
        Map<String, ?> metadata = changedFields(arguments, audited);
        audit.record(actorId, audited.action(), audited.targetType(), targetId, "SUCCESS", metadata);
        return result;
    }

    private static Long actorId(Object[] arguments, int actorParameter) {
        if (actorParameter >= 0) {
            Object actor = argument(arguments, actorParameter);
            if (actor instanceof UserPrincipal principal) return principal.userId();
            return null;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal.userId();
        }
        return null;
    }

    private static String targetId(Object[] arguments, Object result, Audited audited) {
        if (audited.targetIdFromResult()) {
            if (result == null) return null;
            Object value = result;
            if (result instanceof Map<?, ?> map) value = map.get("id");
            return value == null ? null : value.toString();
        }
        Object value = argument(arguments, audited.targetIdParameter());
        return value == null ? null : value.toString();
    }

    private static Map<String, ?> changedFields(Object[] arguments, Audited audited) {
        List<String> changedFields = List.of(audited.changedFields());
        if (audited.changedFieldsParameter() >= 0) {
            Object value = argument(arguments, audited.changedFieldsParameter());
            if (!(value instanceof Map<?, ?> fields)) return null;
            changedFields = fields.keySet().stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .sorted()
                    .toList();
        }
        return changedFields.isEmpty() ? null : Map.of("changedFields", changedFields);
    }

    private static Object argument(Object[] arguments, int index) {
        if (index < 0 || index >= arguments.length) {
            throw new IllegalStateException("Audited method parameter index is invalid");
        }
        return arguments[index];
    }
}
