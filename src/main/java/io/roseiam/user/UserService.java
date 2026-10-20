package io.roseiam.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.roseiam.audit.AuditService;
import io.roseiam.audit.Audited;
import io.roseiam.config.ApiException;
import io.roseiam.config.RequestContext;
import io.roseiam.identity.TokenService;
import io.roseiam.identity.UserSessionService;
import io.roseiam.platformsetting.InvitationMailRequested;
import io.roseiam.role.RoleService;
import io.roseiam.shared.UserPrincipal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserMapper users;
    private final RoleService roles;
    private final UserInvitationMapper invitations;
    private final UserSessionService sessions;
    private final TokenService tokens;
    private final PasswordEncoder passwords;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public UserService(
            UserMapper users,
            RoleService roles,
            UserInvitationMapper invitations,
            UserSessionService sessions,
            TokenService tokens,
            PasswordEncoder passwords,
            AuditService audit,
            ApplicationEventPublisher events,
            Clock clock) {
        this.users = users;
        this.roles = roles;
        this.invitations = invitations;
        this.sessions = sessions;
        this.tokens = tokens;
        this.passwords = passwords;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    @PreAuthorize("hasAuthority('users:invite')")
    @Transactional
    public UserDtos.CreatedInvitation invite(UserDtos.UserCreateRequest request, UserPrincipal actor) {
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
        Set<Long> requestedRoleIds = request.roleIds() == null ? Set.of() : request.roleIds();
        roles.assignOnInvitation(user.getId(), requestedRoleIds, actor.userId());
        String token = tokens.create();
        UserInvitationEntity invitation = new UserInvitationEntity();
        invitation.userId = user.getId();
        invitation.tokenHash = tokens.hash(token);
        invitation.expiresAt = now.plus(Duration.ofHours(72));
        invitation.createdBy = actor.userId();
        invitation.createdAt = now;
        invitations.insert(invitation);
        audit.record(actor.userId(), "user.invited", "User", user.getId().toString(), "SUCCESS", null);
        events.publishEvent(new InvitationMailRequested(email, token, RequestContext.requestId()));
        return new UserDtos.CreatedInvitation(
                new UserDtos.UserCreateResponse(invitation.id, invitation.expiresAt),
                new UserDtos.InvitationDelivery(email, token));
    }

    @Transactional
    public UserDtos.CreatedInvitation createBootstrapInvitation(String rawEmail) {
        if (roles.activePlatformAdminExistsForBootstrap()) return null;
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
            roles.assignBootstrapPlatformAdmin(user.getId());
        } else if (!"INVITED".equals(user.getStatus()) || !roles.hasPlatformAdminRole(user.getId())) {
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
        events.publishEvent(new InvitationMailRequested(email, token, RequestContext.requestId()));
        return new UserDtos.CreatedInvitation(
                new UserDtos.UserCreateResponse(invitation.id, invitation.expiresAt),
                new UserDtos.InvitationDelivery(email, token));
    }

    @Transactional
    public void accept(UserDtos.UserAcceptInvitationRequest request) {
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
    public UserDtos.UserPageResponse list(int page, int size, String status) {
        int p = Math.max(0, page), n = Math.min(100, Math.max(1, size));
        LambdaQueryWrapper<UserEntity> filter = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank())
            filter.eq(UserEntity::getStatus, status.trim().toUpperCase(java.util.Locale.ROOT));
        long total = users.selectCount(filter);
        Page<UserEntity> result = users.selectPage(new Page<>((long) p + 1, n), filter.orderByAsc(UserEntity::getId));
        List<UserDtos.UserResponse> items = summaries(result.getRecords());
        return new UserDtos.UserPageResponse(items, p, n, total, (int) Math.ceil((double) total / n));
    }

    @PreAuthorize("hasAuthority('users:read')")
    @Transactional(readOnly = true)
    public UserDtos.UserResponse getUser(long id) {
        return listOne(id);
    }

    @PreAuthorize("hasAuthority('users:write')")
    @Transactional
    @Audited(
            action = "user.updated",
            targetType = "User",
            targetIdParameter = 0,
            changedFields = {"name"},
            actorParameter = 2)
    public UserDtos.UserResponse updateName(long id, String name, UserPrincipal actor) {
        return updateNameInternal(id, name, actor);
    }

    @Transactional
    @Audited(
            action = "user.updated",
            targetType = "User",
            targetIdParameter = 0,
            changedFields = {"name"},
            actorParameter = 2)
    public UserDtos.UserResponse updateOwnName(long id, String name, UserPrincipal actor) {
        return updateNameInternal(id, name, actor);
    }

    private UserDtos.UserResponse updateNameInternal(long id, String name, UserPrincipal actor) {
        int n = users.update(
                null,
                new LambdaUpdateWrapper<UserEntity>()
                        .eq(UserEntity::getId, id)
                        .set(UserEntity::getName, name)
                        .set(UserEntity::getUpdatedAt, clock.instant()));
        if (n == 0) throw ApiException.notFound("User not found");
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
            sessions.revokeAllForUser(id, clock.instant());
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

    public UserDtos.UserResponse listOne(long id) {
        UserEntity user = users.selectById(id);
        if (user == null) throw ApiException.notFound("User not found");
        return summary(user);
    }

    private UserDtos.UserResponse summary(UserEntity user) {
        return summary(user, roles.roleCodesForUser(user.getId()));
    }

    private List<UserDtos.UserResponse> summaries(List<UserEntity> users) {
        if (users.isEmpty()) return List.of();
        Map<Long, List<String>> roleCodesByUser = roles.roleCodesForUsers(
                users.stream().map(UserEntity::getId).collect(java.util.stream.Collectors.toSet()));
        return users.stream()
                .map(user -> summary(user, roleCodesByUser.getOrDefault(user.getId(), List.of())))
                .toList();
    }

    private UserDtos.UserResponse summary(UserEntity user, List<String> roleCodes) {
        return new UserDtos.UserResponse(
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
        roles.ensureNotLastPlatformAdmin(id);
    }
}
