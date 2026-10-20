package io.roseiam.audit;

import io.roseiam.shared.UserPrincipal;
import java.util.Map;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class AuditAspect {
    private static final Logger logger = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditService audit;

    public AuditAspect(AuditService audit) {
        this.audit = audit;
    }

    @Around("@annotation(audited)")
    public Object recordAuditedOperation(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        try {
            Object result = joinPoint.proceed();
            if (audited.recordOnlyWhenResultTrue() && !Boolean.TRUE.equals(result)) return result;
            if (audited.recordOnlyWhenResultNotNull() && result == null) return null;
            String action = action(joinPoint.getArgs(), audited);
            String targetId = targetId(joinPoint.getArgs(), result, audited);
            if (audited.recordFailure()) {
                recordFailureSafely(action, audited.targetType(), targetId);
            } else {
                audit.record(actorId(joinPoint.getArgs()), action, audited.targetType(), targetId, "SUCCESS");
            }
            return result;
        } catch (Throwable exception) {
            if (audited.recordFailure()) {
                recordFailureSafely(action(joinPoint.getArgs(), audited), audited.targetType(), null);
            }
            throw exception;
        }
    }

    private static String action(Object[] arguments, Audited audited) {
        if (audited.actionParameter() < 0) return audited.action();
        Object value = argument(arguments, audited.actionParameter());
        if (!(value instanceof Boolean flag)) throw new IllegalStateException("Audit action parameter must be boolean");
        return flag ? audited.actionWhenTrue() : audited.actionWhenFalse();
    }

    private static Long actorId(Object[] arguments) {
        for (Object argument : arguments) {
            if (argument instanceof UserPrincipal principal) return principal.userId();
        }
        return null;
    }

    private static String targetId(Object[] arguments, Object result, Audited audited) {
        if (audited.targetIdFromResult()) {
            if (result == null) return null;
            if (result instanceof Map<?, ?> map) return string(map.get("id"));
            throw new IllegalStateException("Audited result does not expose an ID");
        }
        return audited.targetIdParameter() < 0 ? null : string(argument(arguments, audited.targetIdParameter()));
    }

    private static String string(Object value) {
        return value == null ? null : value.toString();
    }

    private static Object argument(Object[] arguments, int index) {
        if (index < 0 || index >= arguments.length) throw new IllegalStateException("Audit parameter index is invalid");
        return arguments[index];
    }

    private void recordFailureSafely(String action, String targetType, String targetId) {
        try {
            audit.recordFailure(action, targetType, targetId);
        } catch (RuntimeException exception) {
            logger.error("Could not persist failed audit event action={}", action, exception);
        }
    }
}
