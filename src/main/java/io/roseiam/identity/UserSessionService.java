package io.roseiam.identity;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class UserSessionService {
    private final UserSessionMapper sessions;

    public UserSessionService(UserSessionMapper sessions) {
        this.sessions = sessions;
    }

    public void revokeAllForUser(long userId, Instant revokedAt) {
        sessions.update(
                null,
                new LambdaUpdateWrapper<UserSessionEntity>()
                        .eq(UserSessionEntity::getUserId, userId)
                        .isNull(UserSessionEntity::getRevokedAt)
                        .set(UserSessionEntity::getRevokedAt, revokedAt));
    }
}
