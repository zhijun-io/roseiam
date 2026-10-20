package io.roseiam.audit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.roseiam.config.ApiException;
import io.roseiam.shared.UserPrincipal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AuditQueryService {
    private final AuditMapper mapper;
    private final AuditService audit;
    private final JsonMapper jsonMapper;

    public AuditQueryService(AuditMapper mapper, AuditService audit, JsonMapper jsonMapper) {
        this.mapper = mapper;
        this.audit = audit;
        this.jsonMapper = jsonMapper;
    }

    @PreAuthorize("hasAuthority('audit-events:read')")
    @Transactional
    public Map<String, Object> page(
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
        int p = Math.max(0, page), n = Math.min(100, Math.max(1, size));
        LambdaQueryWrapper<AuditEventEntity> filter = filter(actor, action, targetType, targetId, result, from, to);
        long total = mapper.selectCount(filter);
        LambdaQueryWrapper<AuditEventEntity> rows = filter(actor, action, targetType, targetId, result, from, to)
                .orderByDesc(AuditEventEntity::getOccurredAt, AuditEventEntity::getId)
                .last("LIMIT " + n + " OFFSET " + ((long) p * n));
        List<Map<String, Object>> items =
                mapper.selectList(rows).stream().map(this::toMap).toList();
        List<String> filtersUsed = filtersUsed(actor, action, targetType, targetId, result, from, to);
        audit.record(
                principal.userId(),
                "audit.events.queried",
                "AuditEvent",
                null,
                "SUCCESS",
                Map.of("filtersUsed", filtersUsed));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", items);
        response.put("page", p);
        response.put("size", n);
        response.put("totalElements", total);
        response.put("totalPages", (int) Math.ceil((double) total / n));
        return response;
    }

    @PreAuthorize("hasAuthority('audit-events:read')")
    @Transactional
    public Map<String, Object> get(long id, UserPrincipal principal) {
        AuditEventEntity event = mapper.selectById(id);
        if (event == null) throw ApiException.notFound("Audit event not found");
        audit.record(
                principal.userId(),
                "audit.event.read",
                "AuditEvent",
                null,
                "SUCCESS",
                Map.of("filtersUsed", List.of("id")));
        return toMap(event);
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

    private static List<String> filtersUsed(
            Long actor, String action, String targetType, String targetId, String result, Instant from, Instant to) {
        List<String> fields = new ArrayList<>();
        if (actor != null) fields.add("actorUserId");
        if (action != null && !action.isBlank()) fields.add("action");
        if (targetType != null && !targetType.isBlank()) fields.add("targetType");
        if (targetId != null && !targetId.isBlank()) fields.add("targetId");
        if (result != null && !result.isBlank()) fields.add("result");
        if (from != null) fields.add("from");
        if (to != null) fields.add("to");
        return fields;
    }

    private Map<String, Object> toMap(AuditEventEntity event) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", event.id);
        value.put("occurredAt", event.occurredAt == null ? null : event.occurredAt.toString());
        value.put("actorUserId", event.actorUserId);
        value.put("action", event.action);
        value.put("targetType", event.targetType);
        value.put("targetId", event.targetId);
        value.put("result", event.result);
        value.put("requestId", event.requestId);
        value.put(
                "metadata",
                event.safeMetadata == null
                        ? null
                        : jsonMapper.readValue(
                                event.safeMetadata,
                                new tools.jackson.core.type.TypeReference<Map<String, Object>>() {}));
        return value;
    }
}
