package io.roseiam.audit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.roseiam.config.ApiException;
import io.roseiam.config.RequestContext;
import io.roseiam.shared.UserPrincipal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final AuditMapper mapper;
    private final Clock clock;

    public AuditService(AuditMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long actorId, String action, String targetType, String targetId, String result) {
        AuditEventEntity event = new AuditEventEntity();
        event.occurredAt = clock.instant();
        event.actorUserId = actorId;
        event.action = action;
        event.targetType = targetType;
        event.targetId = targetId;
        event.result = result;
        event.requestId = RequestContext.requestId();
        mapper.insert(event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String action, String targetType, String targetId) {
        record(null, action, targetType, targetId, "FAILURE");
    }

    @PreAuthorize("hasAuthority('audit-events:read')")
    @Transactional
    @Audited(action = "audit.events.queried", targetType = "AuditEvent")
    public Page<AuditDtos.AuditEventResponse> page(
            int page,
            int size,
            Long actor,
            String action,
            String targetType,
            String targetId,
            String result,
            Instant from,
            Instant to,
            UserPrincipal principal) {
        int p = Math.max(1, page), n = Math.min(100, Math.max(1, size));
        LambdaQueryWrapper<AuditEventEntity> rows = filter(actor, action, targetType, targetId, result, from, to)
                .orderByDesc(AuditEventEntity::getOccurredAt, AuditEventEntity::getId);
        Page<AuditEventEntity> resultPage = mapper.selectPage(new Page<>((long) p, n), rows);
        List<AuditDtos.AuditEventResponse> records =
                resultPage.getRecords().stream().map(AuditService::toResponse).toList();
        return new Page<AuditDtos.AuditEventResponse>(resultPage.getCurrent(), n, resultPage.getTotal())
                .setRecords(records);
    }

    @PreAuthorize("hasAuthority('audit-events:read')")
    @Transactional
    @Audited(action = "audit.event.read", targetType = "AuditEvent", targetIdParameter = 0)
    public AuditDtos.AuditEventResponse get(long id, UserPrincipal principal) {
        AuditEventEntity event = mapper.selectById(id);
        if (event == null) throw ApiException.notFound("Audit event not found");
        return toResponse(event);
    }

    private static LambdaQueryWrapper<AuditEventEntity> filter(
            Long actor, String action, String targetType, String targetId, String result, Instant from, Instant to) {
        return new LambdaQueryWrapper<AuditEventEntity>()
                .eq(actor != null, AuditEventEntity::getActorUserId, actor)
                .eq(action != null && !action.isBlank(), AuditEventEntity::getAction, action)
                .eq(targetType != null && !targetType.isBlank(), AuditEventEntity::getTargetType, targetType)
                .eq(targetId != null && !targetId.isBlank(), AuditEventEntity::getTargetId, targetId)
                .eq(result != null && !result.isBlank(), AuditEventEntity::getResult, result)
                .ge(from != null, AuditEventEntity::getOccurredAt, from)
                .le(to != null, AuditEventEntity::getOccurredAt, to);
    }

    private static AuditDtos.AuditEventResponse toResponse(AuditEventEntity event) {
        return new AuditDtos.AuditEventResponse(
                event.id,
                event.occurredAt == null ? null : event.occurredAt.toString(),
                event.actorUserId,
                event.action,
                event.targetType,
                event.targetId,
                event.result,
                event.requestId);
    }
}
