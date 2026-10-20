package io.roseiam.identity;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.roseiam.role.AuthorizationQuery;
import io.roseiam.shared.UserPrincipal;
import io.roseiam.user.UserEntity;
import io.roseiam.user.UserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class OpaqueBearerFilter extends OncePerRequestFilter {
    private final UserSessionMapper sessions;
    private final UserMapper users;
    private final TokenService tokens;
    private final AuthorizationQuery authorization;
    private final Clock clock;

    public OpaqueBearerFilter(
            UserSessionMapper sessions,
            UserMapper users,
            TokenService tokens,
            AuthorizationQuery authorization,
            Clock clock) {
        this.sessions = sessions;
        this.users = users;
        this.tokens = tokens;
        this.authorization = authorization;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null
                && header.startsWith("Bearer ")
                && !request.getRequestURI().equals("/api/auth/logout")) authenticate(header.substring(7));
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticate(String raw) {
        if (raw.isBlank()) return;
        UserSessionEntity session = sessions.selectOne(
                new LambdaQueryWrapper<UserSessionEntity>().eq(UserSessionEntity::getTokenHash, tokens.hash(raw)));
        if (session == null) return;
        Instant now = clock.instant();
        if (session.revokedAt != null
                || !now.isBefore(session.idleExpiresAt)
                || !now.isBefore(session.absoluteExpiresAt)) return;
        UserEntity user = users.selectById(session.userId);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            sessions.update(
                    null,
                    new LambdaUpdateWrapper<UserSessionEntity>()
                            .eq(UserSessionEntity::getId, session.id)
                            .isNull(UserSessionEntity::getRevokedAt)
                            .set(UserSessionEntity::getRevokedAt, now));
            return;
        }
        sessions.update(
                null,
                new LambdaUpdateWrapper<UserSessionEntity>()
                        .eq(UserSessionEntity::getId, session.id)
                        .set(UserSessionEntity::getLastActivityAt, now)
                        .set(UserSessionEntity::getIdleExpiresAt, now.plusSeconds(900)));
        UserPrincipal principal = new UserPrincipal(
                user.getId(),
                user.getEmail(),
                authorization.permissions(user.getId()),
                authorization.roles(user.getId()));
        var authorities = principal.permissionCodes().stream()
                .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                .toList();
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities));
    }
}
