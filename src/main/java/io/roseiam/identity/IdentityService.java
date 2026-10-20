package io.roseiam.identity;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.roseiam.audit.Audited;
import io.roseiam.config.ApiException;
import io.roseiam.config.AppProperties;
import io.roseiam.config.RequestContext;
import io.roseiam.platformsetting.PasswordResetRequest;
import io.roseiam.user.UserDirectoryService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService {
    private final UserSessionMapper sessions;
    private final UserDirectoryService users;
    private final PasswordResetMapper resets;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Duration idleTimeout;
    private final Duration absoluteTimeout;

    public IdentityService(
            UserSessionMapper sessions,
            UserDirectoryService users,
            PasswordResetMapper resets,
            PasswordEncoder passwords,
            TokenService tokens,
            ApplicationEventPublisher events,
            Clock clock,
            AppProperties properties) {
        this.sessions = sessions;
        this.users = users;
        this.resets = resets;
        this.passwords = passwords;
        this.tokens = tokens;
        this.events = events;
        this.clock = clock;
        this.idleTimeout = properties.getSecurity().getIdleSessionTimeout();
        this.absoluteTimeout = properties.getSecurity().getAbsoluteSessionTimeout();
    }

    @Transactional
    @Audited(action = "auth.login", targetType = "User", recordFailure = true)
    public AuthDtos.LoginResponse createSessionAfterAuthentication(String email) {
        var user = users.findByEmail(email.trim().toLowerCase(Locale.ROOT));
        return createSession(user);
    }

    private AuthDtos.LoginResponse createSession(UserDirectoryService.UserAccount user) {
        if (user == null || user.emailVerifiedAt() == null || !"ACTIVE".equals(user.status())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_credentials", "Email or password is invalid");
        }
        Instant now = clock.instant(), idle = now.plus(idleTimeout), absolute = now.plus(absoluteTimeout);
        String token = tokens.create();
        UserSessionEntity session = new UserSessionEntity();
        session.userId = user.id();
        session.tokenHash = tokens.hash(token);
        session.createdAt = now;
        session.lastActivityAt = now;
        session.idleExpiresAt = idle;
        session.absoluteExpiresAt = absolute;
        sessions.insert(session);
        return new AuthDtos.LoginResponse(token, "Bearer", idle, absolute);
    }

    @Transactional
    @Audited(action = "auth.login", targetType = "User", recordFailure = true)
    public void recordAuthenticationFailure() {}

    @Transactional
    public void logout(String header) {
        if (header == null || !header.startsWith("Bearer ") || header.length() <= 7) throw ApiException.unauthorized();
        String hash = tokens.hash(header.substring(7));
        int revoked = sessions.update(
                null,
                new LambdaUpdateWrapper<UserSessionEntity>()
                        .eq(UserSessionEntity::getTokenHash, hash)
                        .isNull(UserSessionEntity::getRevokedAt)
                        .set(UserSessionEntity::getRevokedAt, clock.instant()));
        if (revoked == 0
                && sessions.selectCount(
                                new LambdaQueryWrapper<UserSessionEntity>().eq(UserSessionEntity::getTokenHash, hash))
                        == 0) {
            throw ApiException.unauthorized();
        }
    }

    @Transactional
    @Audited(action = "password.reset.requested", targetType = "User")
    public void requestPasswordReset(String rawEmail) {
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        UserDirectoryService.UserAccount user = users.findVerifiedByEmail(email);
        if (user != null) {
            Instant now = clock.instant();
            resets.update(
                    null,
                    new LambdaUpdateWrapper<PasswordResetEntity>()
                            .eq(PasswordResetEntity::getUserId, user.id())
                            .isNull(PasswordResetEntity::getUsedAt)
                            .isNull(PasswordResetEntity::getRevokedAt)
                            .set(PasswordResetEntity::getRevokedAt, now));
            String token = tokens.create();
            PasswordResetEntity reset = new PasswordResetEntity();
            reset.userId = user.id();
            reset.tokenHash = tokens.hash(token);
            reset.expiresAt = now.plus(Duration.ofMinutes(30));
            reset.createdAt = now;
            resets.insert(reset);
            events.publishEvent(new PasswordResetRequest(user.email(), token, RequestContext.requestId()));
        }
    }

    @Transactional
    @Audited(action = "password.reset.completed", targetType = "User")
    public void completePasswordReset(String token, String password) {
        Instant now = clock.instant();
        PasswordResetEntity reset = resets.selectOne(new LambdaQueryWrapper<PasswordResetEntity>()
                .eq(PasswordResetEntity::getTokenHash, tokens.hash(token))
                .isNull(PasswordResetEntity::getUsedAt)
                .isNull(PasswordResetEntity::getRevokedAt)
                .gt(PasswordResetEntity::getExpiresAt, now)
                .last("FOR UPDATE"));
        if (reset == null) throw ApiException.badRequest("Password reset token is invalid or expired");
        users.updatePassword(reset.userId, passwords.encode(password), now);
        reset.usedAt = now;
        resets.updateById(reset);
        sessions.update(
                null,
                new LambdaUpdateWrapper<UserSessionEntity>()
                        .eq(UserSessionEntity::getUserId, reset.userId)
                        .isNull(UserSessionEntity::getRevokedAt)
                        .set(UserSessionEntity::getRevokedAt, now));
        resets.update(
                null,
                new LambdaUpdateWrapper<PasswordResetEntity>()
                        .eq(PasswordResetEntity::getUserId, reset.userId)
                        .ne(PasswordResetEntity::getId, reset.id)
                        .isNull(PasswordResetEntity::getUsedAt)
                        .isNull(PasswordResetEntity::getRevokedAt)
                        .set(PasswordResetEntity::getRevokedAt, now));
    }
}
