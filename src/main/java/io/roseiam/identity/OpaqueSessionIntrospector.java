package io.roseiam.identity;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.roseiam.role.RolePermissionService;
import io.roseiam.shared.UserPrincipal;
import io.roseiam.user.UserService;
import java.time.Clock;
import java.time.Instant;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.stereotype.Component;

@Component
public class OpaqueSessionIntrospector implements OpaqueTokenIntrospector {
    private final UserSessionMapper sessions;
    private final UserService users;
    private final TokenService tokens;
    private final RolePermissionService authorization;
    private final Clock clock;
    private final java.time.Duration idleTimeout;

    public OpaqueSessionIntrospector(
            UserSessionMapper sessions,
            UserService users,
            TokenService tokens,
            RolePermissionService authorization,
            Clock clock,
            io.roseiam.config.AppProperties properties) {
        this.sessions = sessions;
        this.users = users;
        this.tokens = tokens;
        this.authorization = authorization;
        this.clock = clock;
        this.idleTimeout = properties.getSecurity().getIdleSessionTimeout();
    }

    @Override
    public OAuth2AuthenticatedPrincipal introspect(String token) {
        try {
            return introspectAvailableToken(token);
        } catch (DataAccessException exception) {
            throw new AuthenticationServiceException("Identity store is unavailable", exception);
        }
    }

    private OAuth2AuthenticatedPrincipal introspectAvailableToken(String token) {
        if (token == null || token.isBlank()) throw invalidToken();
        UserSessionEntity session = sessions.selectOne(
                new LambdaQueryWrapper<UserSessionEntity>().eq(UserSessionEntity::getTokenHash, tokens.hash(token)));
        Instant now = clock.instant();
        if (session == null
                || session.revokedAt != null
                || !now.isBefore(session.idleExpiresAt)
                || !now.isBefore(session.absoluteExpiresAt)) {
            throw invalidToken();
        }
        UserService.UserAccount user = users.findById(session.userId);
        if (user == null || user.emailVerifiedAt() == null || !"ACTIVE".equals(user.status())) {
            sessions.update(
                    null,
                    new LambdaUpdateWrapper<UserSessionEntity>()
                            .eq(UserSessionEntity::getId, session.id)
                            .isNull(UserSessionEntity::getRevokedAt)
                            .set(UserSessionEntity::getRevokedAt, now));
            throw invalidToken();
        }
        sessions.update(
                null,
                new LambdaUpdateWrapper<UserSessionEntity>()
                        .eq(UserSessionEntity::getId, session.id)
                        .isNull(UserSessionEntity::getRevokedAt)
                        .set(UserSessionEntity::getLastActivityAt, now)
                        .set(UserSessionEntity::getIdleExpiresAt, now.plus(idleTimeout)));
        UserPrincipal principal = new UserPrincipal(
                user.id(), user.email(), authorization.permissions(user.id()), authorization.roles(user.id()));
        return principal;
    }

    private static BadOpaqueTokenException invalidToken() {
        return new BadOpaqueTokenException("Invalid bearer token");
    }
}
