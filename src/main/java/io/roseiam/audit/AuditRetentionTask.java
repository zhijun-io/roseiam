package io.roseiam.audit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.roseiam.persistence.entity.AuditEventEntity;
import java.time.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AuditRetentionTask {

    private final AuditMapper mapper;
    private final Clock clock;

    public AuditRetentionTask(AuditMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Scheduled(cron = "0 30 2 * * *", zone = "UTC")
    @Transactional
    public void deleteExpiredEvents() {
        mapper.delete(new LambdaQueryWrapper<AuditEventEntity>()
                .lt(AuditEventEntity::getOccurredAt, clock.instant().minus(java.time.Duration.ofDays(365))));
    }
}
