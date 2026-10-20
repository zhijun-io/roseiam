package io.roseiam.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.roseiam.audit.Audited;
import io.roseiam.config.ApiException;
import io.roseiam.config.RequestContext;
import io.roseiam.identity.TokenService;
import io.roseiam.identity.UserSessionService;
import io.roseiam.platformsetting.InvitationMailRequest;
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
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public UserService(
            UserMapper users,
            RoleService roles,
            UserInvitationMapper invitations,
            UserSessionService sessions,
            TokenService tokens,
            PasswordEncoder passwords,
            ApplicationEventPublisher events,
            Clock clock) {
        this.users = users;
        this.roles = roles;
        this.invitations = invitations;
        this.sessions = sessions;
        this.tokens = tokens;
        this.passwords = passwords;
        this.events = events;
        this.clock = clock;
    }

    @PreAuthorize("hasAuthority('users:invite')")
    @Transactional
    @Audited(action = "user.invited", targetType = "User")
    public UserDtos.CreatedInvitation invite(UserDtos.UserCreateRequest request, UserPrincipal actor) {
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);
        Instant now = clock.instant();
        UserEntity user = users.selectOne(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getEmail, email));
        if (user != null) {
            if (!"INVITED".equals(user.getStatus())) throw ApiException.conflict("User already exists");
            revokePendingInvitations(user.getId(), now);
        } else {
            user = createInvitedUser(email, now);
        }
        Set<Long> requestedRoleIds = request.roleIds() == null ? Set.of() : request.roleIds();
        roles.assignOnInvitation(user.getId(), requestedRoleIds, actor);
        return createInvitation(user.getId(), email, actor.userId(), now);
    }

    @Transactional
    @Audited(action = "bootstrap.admin.invited", targetType = "User", recordOnlyWhenResultNotNull = true)
    public UserDtos.CreatedInvitation createBootstrapInvitation(String rawEmail) {
        if (roles.activePlatformAdminExistsForBootstrap()) return null;
        String email = rawEmail.trim().toLowerCase(java.util.Locale.ROOT);
        Instant now = clock.instant();
        UserEntity user = users.selectOne(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getEmail, email));
        if (user == null) {
            user = createInvitedUser(email, now);
            roles.assignBootstrapPlatformAdmin(user.getId());
        } else if (!"INVITED".equals(user.getStatus()) || !roles.hasPlatformAdminRole(user.getId())) {
            throw ApiException.conflict("Bootstrap email already belongs to an account");
        }
        revokePendingInvitations(user.getId(), now);
        return createInvitation(user.getId(), email, null, now);
    }

    @Transactional
    @Audited(action = "user.invitation.accepted", targetType = "User")
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
    }

    @PreAuthorize("hasAuthority('users:read')")
    @Transactional(readOnly = true)
    public UserDtos.UserPageResponse list(int page, int size, String status) {
        int p = Math.max(0, page), n = Math.min(100, Math.max(1, size));
        LambdaQueryWrapper<UserEntity> filter = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank())
            filter.eq(UserEntity::getStatus, status.trim().toUpperCase(java.util.Locale.ROOT));
        Page<UserEntity> result = users.selectPage(new Page<>((long) p + 1, n), filter.orderByAsc(UserEntity::getId));
        List<UserDtos.UserResponse> items = summaries(result.getRecords());
        return new UserDtos.UserPageResponse(items, p, n, result.getTotal(), (int) result.getPages());
    }

    @PreAuthorize("hasAuthority('users:read')")
    @Transactional(readOnly = true)
    public UserDtos.UserResponse getUser(long id) {
        return listOne(id);
    }

    @PreAuthorize("hasAuthority('users:write')")
    @Transactional
    @Audited(action = "user.updated", targetType = "User", targetIdParameter = 0)
    public UserDtos.UserResponse updateName(long id, String name, UserPrincipal actor) {
        return updateNameInternal(id, name);
    }

    @Transactional
    @Audited(action = "user.updated", targetType = "User", targetIdParameter = 0)
    public UserDtos.UserResponse updateOwnName(long id, String name, UserPrincipal actor) {
        return updateNameInternal(id, name);
    }

    private UserDtos.UserResponse updateNameInternal(long id, String name) {
        UserEntity user = users.selectById(id);
        if (user == null) throw ApiException.notFound("User not found");
        if (java.util.Objects.equals(user.getName(), name)) return summary(user);
        Instant updatedAt = clock.instant();
        int n = users.update(
                null,
                new LambdaUpdateWrapper<UserEntity>()
                        .eq(UserEntity::getId, id)
                        .set(UserEntity::getName, name)
                        .set(UserEntity::getUpdatedAt, updatedAt));
        if (n == 0) throw ApiException.notFound("User not found");
        user.setName(name);
        user.setUpdatedAt(updatedAt);
        return summary(user);
    }

    @PreAuthorize("hasAuthority('users:disable')")
    @Transactional
    @Audited(action = "user.disabled", targetType = "User", targetIdParameter = 0, recordOnlyWhenResultTrue = true)
    public boolean disable(long id, UserPrincipal actor) {
        if (users.selectById(id) == null) throw ApiException.notFound("User not found");
        roles.ensureNotLastPlatformAdmin(id);
        int changed = users.update(
                null,
                new LambdaUpdateWrapper<UserEntity>()
                        .eq(UserEntity::getId, id)
                        .ne(UserEntity::getStatus, "DISABLED")
                        .set(UserEntity::getStatus, "DISABLED")
                        .set(UserEntity::getUpdatedAt, clock.instant()));
        if (changed > 0) {
            sessions.revokeAllForUser(id, clock.instant());
        }
        return changed > 0;
    }

    @PreAuthorize("hasAuthority('users:enable')")
    @Transactional
    @Audited(action = "user.enabled", targetType = "User", targetIdParameter = 0, recordOnlyWhenResultTrue = true)
    public boolean enable(long id, UserPrincipal actor) {
        int changed = users.update(
                null,
                new LambdaUpdateWrapper<UserEntity>()
                        .eq(UserEntity::getId, id)
                        .eq(UserEntity::getStatus, "DISABLED")
                        .set(UserEntity::getStatus, "ACTIVE")
                        .set(UserEntity::getUpdatedAt, clock.instant()));
        if (changed == 0 && users.selectById(id) == null) throw ApiException.notFound("User not found");
        return changed > 0;
    }

    public UserDtos.UserResponse listOne(long id) {
        UserEntity user = users.selectById(id);
        if (user == null) throw ApiException.notFound("User not found");
        return summary(user);
    }

    private UserDtos.CreatedInvitation createInvitation(long userId, String email, Long createdBy, Instant now) {
        String token = tokens.create();
        UserInvitationEntity invitation = new UserInvitationEntity();
        invitation.userId = userId;
        invitation.tokenHash = tokens.hash(token);
        invitation.expiresAt = now.plus(Duration.ofHours(72));
        invitation.createdBy = createdBy;
        invitation.createdAt = now;
        invitations.insert(invitation);
        events.publishEvent(new InvitationMailRequest(email, token, RequestContext.requestId()));
        return new UserDtos.CreatedInvitation(
                new UserDtos.UserCreateResponse(invitation.id, invitation.expiresAt),
                new UserDtos.InvitationDelivery(email, token));
    }

    private UserEntity createInvitedUser(String email, Instant now) {
        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setStatus("INVITED");
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        users.insert(user);
        return user;
    }

    private void revokePendingInvitations(long userId, Instant now) {
        invitations.update(
                null,
                new LambdaUpdateWrapper<UserInvitationEntity>()
                        .eq(UserInvitationEntity::getUserId, userId)
                        .isNull(UserInvitationEntity::getUsedAt)
                        .isNull(UserInvitationEntity::getRevokedAt)
                        .set(UserInvitationEntity::getRevokedAt, now));
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
}
