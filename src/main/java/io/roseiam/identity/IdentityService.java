package io.roseiam.identity;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.roseiam.audit.AuditService;
import io.roseiam.config.ApiException;
import io.roseiam.user.UserEntity;
import io.roseiam.user.UserMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService {
    private static final Logger logger = LoggerFactory.getLogger(IdentityService.class);
    private final UserSessionMapper sessions;
    private final UserMapper users;
    private final PasswordRecoveryMapper recoveries;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    private final AuditService audit;
    private final Clock clock;
    private final Duration idleTimeout;
    private final Duration absoluteTimeout;
    private final String dummyHash;

    public IdentityService(
            UserSessionMapper sessions,
            UserMapper users,
            PasswordRecoveryMapper recoveries,
            PasswordEncoder passwords,
            TokenService tokens,
            AuditService audit,
            Clock clock,
            @Value("${app.security.idle-session-timeout:15m}") Duration idle,
            @Value("${app.security.absolute-session-timeout:8h}") Duration absolute) {
        this.sessions = sessions;
        this.users = users;
        this.recoveries = recoveries;
        this.passwords = passwords;
        this.tokens = tokens;
        this.audit = audit;
        this.clock = clock;
        this.idleTimeout = idle;
        this.absoluteTimeout = absolute;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        UserEntity user = users.selectOne(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getEmail, email));
        String hash = user == null ? dummyHash : user.getPasswordHash();
        boolean matches = passwords.matches(request.password(), hash == null ? dummyHash : hash);
        if (user == null || !matches || user.getEmailVerifiedAt() == null || !"ACTIVE".equals(user.getStatus())) {
            try {
                audit.recordFailure(
                        "auth.login",
                        "User",
                        user == null ? null : user.getId().toString(),
                        Map.of("reason", "invalid_credentials"));
            } catch (RuntimeException ex) {
                logger.error("Could not persist failed login audit event");
            }
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_credentials", "Email or password is invalid");
        }
        Instant now = clock.instant(), idle = now.plus(idleTimeout), absolute = now.plus(absoluteTimeout);
        String token = tokens.create();
        UserSessionEntity session = new UserSessionEntity();
        session.userId = user.getId();
        session.tokenHash = tokens.hash(token);
        session.createdAt = now;
        session.lastActivityAt = now;
        session.idleExpiresAt = idle;
        session.absoluteExpiresAt = absolute;
        sessions.insert(session);
        audit.record(user.getId(), "auth.login", "User", user.getId().toString(), "SUCCESS", null);
        return new AuthDtos.LoginResponse(token, "Bearer", idle, absolute);
    }

    @Transactional
    public void logout(String header) {
        if (header == null || !header.startsWith("Bearer ") || header.length() <= 7) throw ApiException.unauthorized();
        String hash = tokens.hash(header.substring(7));
        long count = sessions.selectCount(
                new LambdaQueryWrapper<UserSessionEntity>().eq(UserSessionEntity::getTokenHash, hash));
        if (count == 0) throw ApiException.unauthorized();
        sessions.update(
                null,
                new LambdaUpdateWrapper<UserSessionEntity>()
                        .eq(UserSessionEntity::getTokenHash, hash)
                        .isNull(UserSessionEntity::getRevokedAt)
                        .set(UserSessionEntity::getRevokedAt, clock.instant()));
    }

    @Transactional
    public AuthDtos.RecoveryDelivery requestPasswordRecovery(String rawEmail) {
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        UserEntity user = users.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getEmail, email)
                .isNotNull(UserEntity::getEmailVerifiedAt));
        AuthDtos.RecoveryDelivery delivery = null;
        if (user != null) {
            Instant now = clock.instant();
            recoveries.update(
                    null,
                    new LambdaUpdateWrapper<PasswordRecoveryEntity>()
                            .eq(PasswordRecoveryEntity::getUserId, user.getId())
                            .isNull(PasswordRecoveryEntity::getUsedAt)
                            .isNull(PasswordRecoveryEntity::getRevokedAt)
                            .set(PasswordRecoveryEntity::getRevokedAt, now));
            String token = tokens.create();
            PasswordRecoveryEntity recovery = new PasswordRecoveryEntity();
            recovery.userId = user.getId();
            recovery.tokenHash = tokens.hash(token);
            recovery.expiresAt = now.plus(Duration.ofMinutes(30));
            recovery.createdAt = now;
            recoveries.insert(recovery);
            delivery = new AuthDtos.RecoveryDelivery(user.getEmail(), token);
        }
        audit.record(
                user == null ? null : user.getId(),
                "password.recovery.requested",
                "User",
                user == null ? null : user.getId().toString(),
                "SUCCESS",
                null);
        return delivery;
    }

    @Transactional
    public void completePasswordRecovery(String token, String password) {
        Instant now = clock.instant();
        PasswordRecoveryEntity recovery = recoveries.selectOne(new LambdaQueryWrapper<PasswordRecoveryEntity>()
                .eq(PasswordRecoveryEntity::getTokenHash, tokens.hash(token))
                .isNull(PasswordRecoveryEntity::getUsedAt)
                .isNull(PasswordRecoveryEntity::getRevokedAt)
                .gt(PasswordRecoveryEntity::getExpiresAt, now)
                .last("FOR UPDATE"));
        if (recovery == null) throw ApiException.badRequest("Password recovery token is invalid or expired");
        users.update(
                null,
                new LambdaUpdateWrapper<UserEntity>()
                        .eq(UserEntity::getId, recovery.userId)
                        .set(UserEntity::getPasswordHash, passwords.encode(password))
                        .set(UserEntity::getUpdatedAt, now));
        recoveries.updateById(fillRecovery(recovery, now));
        sessions.update(
                null,
                new LambdaUpdateWrapper<UserSessionEntity>()
                        .eq(UserSessionEntity::getUserId, recovery.userId)
                        .isNull(UserSessionEntity::getRevokedAt)
                        .set(UserSessionEntity::getRevokedAt, now));
        recoveries.update(
                null,
                new LambdaUpdateWrapper<PasswordRecoveryEntity>()
                        .eq(PasswordRecoveryEntity::getUserId, recovery.userId)
                        .ne(PasswordRecoveryEntity::getId, recovery.id)
                        .isNull(PasswordRecoveryEntity::getUsedAt)
                        .isNull(PasswordRecoveryEntity::getRevokedAt)
                        .set(PasswordRecoveryEntity::getRevokedAt, now));
        audit.record(
                recovery.userId, "password.recovery.completed", "User", recovery.userId.toString(), "SUCCESS", null);
    }

    private static PasswordRecoveryEntity fillRecovery(PasswordRecoveryEntity recovery, Instant now) {
        recovery.usedAt = now;
        return recovery;
    }
}
