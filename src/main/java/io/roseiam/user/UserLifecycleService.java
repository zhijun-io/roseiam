package io.roseiam.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.roseiam.audit.AuditService;
import io.roseiam.config.ApiException;
import io.roseiam.config.TokenService;
import io.roseiam.identity.AuthDtos;
import io.roseiam.identity.UserPrincipal;
import io.roseiam.identity.mapper.IdentityMapper;
import io.roseiam.persistence.entity.UserInvitationEntity;
import io.roseiam.persistence.entity.UserRoleEntity;
import io.roseiam.persistence.entity.UserSessionEntity;
import io.roseiam.persistence.mapper.UserInvitationMapper;
import io.roseiam.persistence.mapper.UserRoleMapper;
import io.roseiam.role.RoleEntity;
import io.roseiam.role.RoleMapper;
import io.roseiam.user.persistence.UserEntity;
import io.roseiam.user.persistence.UserMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserLifecycleService {
    private final UserMapper users;
    private final RoleMapper roles;
    private final UserRoleMapper userRoles;
    private final UserInvitationMapper invitations;
    private final IdentityMapper sessions;
    private final TokenService tokens;
    private final PasswordEncoder passwords;
    private final AuditService audit;
    private final Clock clock;

    public UserLifecycleService(
            UserMapper users,
            RoleMapper roles,
            UserRoleMapper userRoles,
            UserInvitationMapper invitations,
            IdentityMapper sessions,
            TokenService tokens,
            PasswordEncoder passwords,
            AuditService audit,
            Clock clock) {
        this.users = users;
        this.roles = roles;
        this.userRoles = userRoles;
        this.invitations = invitations;
        this.sessions = sessions;
        this.tokens = tokens;
        this.passwords = passwords;
        this.audit = audit;
        this.clock = clock;
    }

    @PreAuthorize("hasAuthority('users:invite')")
    @Transactional
    public AuthDtos.CreatedInvitation invite(AuthDtos.InvitationRequest request, UserPrincipal actor) {
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);
        Instant now = clock.instant();
        UserEntity user = users.selectOne(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getEmail, email));
        if (user != null) {
            if (!"INVITED".equals(user.getStatus())) throw ApiException.conflict("User already exists");
            invitations.update(
                    null,
                    new LambdaUpdateWrapper<UserInvitationEntity>()
                            .eq(UserInvitationEntity::getUserId, user.getId())
                            .isNull(UserInvitationEntity::getUsedAt)
                            .isNull(UserInvitationEntity::getRevokedAt)
                            .set(UserInvitationEntity::getRevokedAt, now));
        } else {
            user = new UserEntity();
            user.setEmail(email);
            user.setStatus("INVITED");
            user.setCreatedAt(now);
            user.setUpdatedAt(now);
            users.insert(user);
        }
        if (request.roleIds() != null)
            for (Long roleId : request.roleIds()) {
                if (roles.selectById(roleId) == null) throw ApiException.badRequest("Unknown role");
                if (userRoles.selectCount(new LambdaQueryWrapper<UserRoleEntity>()
                                .eq(UserRoleEntity::getUserId, user.getId())
                                .eq(UserRoleEntity::getRoleId, roleId))
                        == 0) {
                    UserRoleEntity link = new UserRoleEntity();
                    link.userId = user.getId();
                    link.roleId = roleId;
                    link.assignedBy = actor.userId();
                    link.assignedAt = now;
                    userRoles.insert(link);
                }
            }
        String token = tokens.create();
        UserInvitationEntity invitation = new UserInvitationEntity();
        invitation.userId = user.getId();
        invitation.tokenHash = tokens.hash(token);
        invitation.expiresAt = now.plus(Duration.ofHours(72));
        invitation.createdBy = actor.userId();
        invitation.createdAt = now;
        invitations.insert(invitation);
        audit.record(actor.userId(), "user.invited", "User", user.getId().toString(), "SUCCESS", null);
        return new AuthDtos.CreatedInvitation(
                new AuthDtos.InvitationResponse(invitation.id, invitation.expiresAt),
                new AuthDtos.InvitationDelivery(email, token));
    }

    @Transactional
    public AuthDtos.CreatedInvitation createBootstrapInvitation(String rawEmail) {
        RoleEntity adminRole = roles.selectOne(new LambdaQueryWrapper<RoleEntity>()
                .eq(RoleEntity::getCode, "PlatformAdmin")
                .last("FOR UPDATE"));
        if (adminRole == null) throw new IllegalStateException("PlatformAdmin role is not seeded");
        if (activeAdminCount(adminRole.getId()) > 0) return null;
        String email = rawEmail.trim().toLowerCase(java.util.Locale.ROOT);
        Instant now = clock.instant();
        UserEntity user = users.selectOne(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getEmail, email));
        if (user == null) {
            user = new UserEntity();
            user.setEmail(email);
            user.setStatus("INVITED");
            user.setCreatedAt(now);
            user.setUpdatedAt(now);
            users.insert(user);
            UserRoleEntity link = new UserRoleEntity();
            link.userId = user.getId();
            link.roleId = adminRole.getId();
            link.assignedAt = now;
            userRoles.insert(link);
        } else if (!"INVITED".equals(user.getStatus())
                || userRoles.selectCount(new LambdaQueryWrapper<UserRoleEntity>()
                                .eq(UserRoleEntity::getUserId, user.getId())
                                .eq(UserRoleEntity::getRoleId, adminRole.getId()))
                        == 0) {
            throw ApiException.conflict("Bootstrap email already belongs to an account");
        }
        invitations.update(
                null,
                new LambdaUpdateWrapper<UserInvitationEntity>()
                        .eq(UserInvitationEntity::getUserId, user.getId())
                        .isNull(UserInvitationEntity::getUsedAt)
                        .isNull(UserInvitationEntity::getRevokedAt)
                        .set(UserInvitationEntity::getRevokedAt, now));
        String token = tokens.create();
        UserInvitationEntity invitation = new UserInvitationEntity();
        invitation.userId = user.getId();
        invitation.tokenHash = tokens.hash(token);
        invitation.expiresAt = now.plus(Duration.ofHours(72));
        invitation.createdAt = now;
        invitations.insert(invitation);
        audit.record(null, "bootstrap.admin.invited", "User", user.getId().toString(), "SUCCESS", null);
        return new AuthDtos.CreatedInvitation(
                new AuthDtos.InvitationResponse(invitation.id, invitation.expiresAt),
                new AuthDtos.InvitationDelivery(email, token));
    }

    @Transactional
    public void accept(AuthDtos.InvitationAcceptance request) {
        Instant now = clock.instant();
        UserInvitationEntity invitation = invitations.selectOne(new LambdaQueryWrapper<UserInvitationEntity>()
                .eq(UserInvitationEntity::getTokenHash, tokens.hash(request.token()))
                .isNull(UserInvitationEntity::getUsedAt)
                .isNull(UserInvitationEntity::getRevokedAt)
                .gt(UserInvitationEntity::getExpiresAt, now)
                .last("FOR UPDATE"));
        if (invitation == null) throw ApiException.badRequest("Invitation is invalid or expired");
        UserEntity user = new UserEntity();
        user.setPasswordHash(passwords.encode(request.password()));
        user.setEmailVerifiedAt(now);
        user.setName(request.name());
        user.setStatus("ACTIVE");
        user.setUpdatedAt(now);
        int changed = users.update(
                user,
                new LambdaUpdateWrapper<UserEntity>()
                        .eq(UserEntity::getId, invitation.userId)
                        .eq(UserEntity::getStatus, "INVITED"));
        if (changed == 0) throw ApiException.conflict("Invitation is no longer valid");
        invitation.usedAt = now;
        invitations.updateById(invitation);
        audit.record(
                invitation.userId, "user.invitation.accepted", "User", invitation.userId.toString(), "SUCCESS", null);
    }

    @PreAuthorize("hasAuthority('users:read')")
    @Transactional(readOnly = true)
    public AuthDtos.UserPage list(int page, int size, String status) {
        int p = Math.max(0, page), n = Math.min(100, Math.max(1, size));
        LambdaQueryWrapper<UserEntity> filter = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank())
            filter.eq(UserEntity::getStatus, status.trim().toUpperCase(java.util.Locale.ROOT));
        long total = users.selectCount(filter);
        List<AuthDtos.UserSummary> items = users
                .selectList(filter.orderByAsc(UserEntity::getId).last("LIMIT " + n + " OFFSET " + (long) p * n))
                .stream()
                .map(this::summary)
                .toList();
        return new AuthDtos.UserPage(items, p, n, total, (int) Math.ceil((double) total / n));
    }

    @PreAuthorize("hasAuthority('users:read')")
    @Transactional(readOnly = true)
    public AuthDtos.UserSummary getUser(long id) {
        return listOne(id);
    }

    @PreAuthorize("hasAuthority('users:write')")
    @Transactional
    public AuthDtos.UserSummary updateName(long id, String name, UserPrincipal actor) {
        return updateNameInternal(id, name, actor);
    }

    @Transactional
    public AuthDtos.UserSummary updateOwnName(long id, String name, UserPrincipal actor) {
        return updateNameInternal(id, name, actor);
    }

    private AuthDtos.UserSummary updateNameInternal(long id, String name, UserPrincipal actor) {
        int n = users.update(
                null,
                new LambdaUpdateWrapper<UserEntity>()
                        .eq(UserEntity::getId, id)
                        .set(UserEntity::getName, name)
                        .set(UserEntity::getUpdatedAt, clock.instant()));
        if (n == 0) throw ApiException.notFound("User not found");
        audit.record(
                actor.userId(),
                "user.updated",
                "User",
                Long.toString(id),
                "SUCCESS",
                Map.of("changedFields", List.of("name")));
        return listOne(id);
    }

    @PreAuthorize("hasAuthority('users:disable')")
    @Transactional
    public void disable(long id, UserPrincipal actor) {
        if (users.selectById(id) == null) throw ApiException.notFound("User not found");
        protectLastAdmin(id);
        int changed = users.update(
                null,
                new LambdaUpdateWrapper<UserEntity>()
                        .eq(UserEntity::getId, id)
                        .ne(UserEntity::getStatus, "DISABLED")
                        .set(UserEntity::getStatus, "DISABLED")
                        .set(UserEntity::getUpdatedAt, clock.instant()));
        if (changed > 0) {
            sessions.update(
                    null,
                    new LambdaUpdateWrapper<UserSessionEntity>()
                            .eq(UserSessionEntity::getUserId, id)
                            .isNull(UserSessionEntity::getRevokedAt)
                            .set(UserSessionEntity::getRevokedAt, clock.instant()));
            audit.record(actor.userId(), "user.disabled", "User", Long.toString(id), "SUCCESS", null);
        }
    }

    @PreAuthorize("hasAuthority('users:enable')")
    @Transactional
    public void enable(long id, UserPrincipal actor) {
        int changed = users.update(
                null,
                new LambdaUpdateWrapper<UserEntity>()
                        .eq(UserEntity::getId, id)
                        .eq(UserEntity::getStatus, "DISABLED")
                        .set(UserEntity::getStatus, "ACTIVE")
                        .set(UserEntity::getUpdatedAt, clock.instant()));
        if (changed == 0 && users.selectById(id) == null) throw ApiException.notFound("User not found");
        if (changed > 0) audit.record(actor.userId(), "user.enabled", "User", Long.toString(id), "SUCCESS", null);
    }

    public AuthDtos.UserSummary listOne(long id) {
        UserEntity user = users.selectById(id);
        if (user == null) throw ApiException.notFound("User not found");
        return summary(user);
    }

    private AuthDtos.UserSummary summary(UserEntity user) {
        List<Long> roleIds = userRoles
                .selectList(new LambdaQueryWrapper<UserRoleEntity>().eq(UserRoleEntity::getUserId, user.getId()))
                .stream()
                .map(link -> link.roleId)
                .toList();
        List<String> roleCodes = roleIds.isEmpty()
                ? List.of()
                : roles.selectList(new LambdaQueryWrapper<RoleEntity>().in(RoleEntity::getId, roleIds)).stream()
                        .map(RoleEntity::getCode)
                        .toList();
        return new AuthDtos.UserSummary(
                user.getId(),
                user.getEmail(),
                user.getEmailVerifiedAt(),
                user.getName(),
                user.getStatus(),
                roleCodes,
                user.getCreatedAt(),
                user.getUpdatedAt());
    }

    private void protectLastAdmin(long id) {
        RoleEntity admin = roles.selectOne(new LambdaQueryWrapper<RoleEntity>()
                .eq(RoleEntity::getCode, "PlatformAdmin")
                .last("FOR UPDATE"));
        if (admin != null
                && userRoles.selectCount(new LambdaQueryWrapper<UserRoleEntity>()
                                .eq(UserRoleEntity::getUserId, id)
                                .eq(UserRoleEntity::getRoleId, admin.getId()))
                        > 0
                && activeAdminCount(admin.getId()) <= 1)
            throw ApiException.conflict("The last active PlatformAdmin cannot be disabled");
    }

    private long activeAdminCount(long roleId) {
        List<Long> ids =
                userRoles
                        .selectList(new LambdaQueryWrapper<UserRoleEntity>().eq(UserRoleEntity::getRoleId, roleId))
                        .stream()
                        .map(link -> link.userId)
                        .toList();
        return ids.isEmpty()
                ? 0
                : users.selectCount(new LambdaQueryWrapper<UserEntity>()
                        .in(UserEntity::getId, ids)
                        .eq(UserEntity::getStatus, "ACTIVE"));
    }
}
