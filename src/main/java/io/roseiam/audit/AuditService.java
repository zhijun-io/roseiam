package io.roseiam.audit;

import io.roseiam.config.RequestContext;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AuditService {

    private static final List<String> SAFE_KEYS =
            List.of("changedFields", "filtersUsed", "fromStatus", "toStatus", "reason");

    private final AuditMapper mapper;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public AuditService(AuditMapper mapper, JsonMapper jsonMapper, Clock clock) {
        this.mapper = mapper;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(
            Long actorId, String action, String targetType, String targetId, String result, Map<String, ?> metadata) {
        String safeJson = null;
        if (metadata != null) {
            Map<String, Object> safe = new LinkedHashMap<>();
            SAFE_KEYS.forEach(key -> {
                Object value = metadata.get(key);
                if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                    safe.put(key, value);
                } else if (value instanceof List<?> list && list.stream().allMatch(String.class::isInstance)) {
                    safe.put(key, list);
                }
            });
            try {
                safeJson = jsonMapper.writeValueAsString(safe);
            } catch (Exception exception) {
                throw new IllegalStateException("Could not encode audit metadata", exception);
            }
        }
        AuditEventEntity event = new AuditEventEntity();
        event.occurredAt = clock.instant();
        event.actorUserId = actorId;
        event.action = action;
        event.targetType = targetType;
        event.targetId = targetId;
        event.result = result;
        event.requestId = RequestContext.requestId();
        event.safeMetadata = safeJson;
        mapper.insert(event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String action, String targetType, String targetId, Map<String, ?> metadata) {
        record(null, action, targetType, targetId, "FAILURE", metadata);
    }
}
