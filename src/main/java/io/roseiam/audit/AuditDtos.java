package io.roseiam.audit;

public final class AuditDtos {
    private AuditDtos() {}

    public record AuditEventResponse(
            Long id,
            String occurredAt,
            Long actorUserId,
            String action,
            String targetType,
            String targetId,
            String result,
            String requestId) {}
}
