package io.roseiam.role;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.roseiam.audit.AuditService;
import io.roseiam.audit.Audited;
import io.roseiam.config.ApiException;
import io.roseiam.shared.UserPrincipal;
import io.roseiam.user.UserDirectoryService;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleService {
    private final RoleMapper roles;
    private final PermissionMapper permissions;
    private final RolePermissionMapper rolePermissions;
    private final UserRoleMapper userRoles;
    private final UserDirectoryService users;
    private final AuditService audit;
    private final Clock clock;

    public RoleService(
            RoleMapper roles,
            PermissionMapper permissions,
            RolePermissionMapper rolePermissions,
            UserRoleMapper userRoles,
            UserDirectoryService users,
            AuditService audit,
            Clock clock) {
        this.roles = roles;
        this.permissions = permissions;
        this.rolePermissions = rolePermissions;
        this.userRoles = userRoles;
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    @PreAuthorize("hasAuthority('roles:read')")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> permissions() {
        return permissions
                .selectList(new LambdaQueryWrapper<PermissionEntity>().orderByAsc(PermissionEntity::getCode))
                .stream()
                .map(p -> Map.<String, Object>of("id", p.id, "code", p.code, "description", p.description))
                .toList();
    }

    @PreAuthorize("hasAuthority('roles:read')")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> roles() {
        return roles.selectList(new LambdaQueryWrapper<RoleEntity>().orderByAsc(RoleEntity::getName)).stream()
                .map(RoleService::roleView)
                .toList();
    }

    @PreAuthorize("hasAuthority('roles:read')")
    @Transactional(readOnly = true)
    public Map<String, Object> get(long id) {
        return details(id);
    }

    @PreAuthorize("hasAuthority('roles:write')")
    @Transactional
    @Audited(action = "role.created", targetType = "Role", targetIdFromResult = true, actorParameter = 3)
    public Map<String, Object> create(String name, String description, Set<Long> permissionIds, UserPrincipal actor) {
        String code = "Custom_" + name.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        if (roles.selectCount(new LambdaQueryWrapper<RoleEntity>()
                        .eq(RoleEntity::getName, name)
                        .or()
                        .eq(RoleEntity::getCode, code))
                > 0) throw ApiException.conflict("Role name already exists");
        RoleEntity role = new RoleEntity();
        role.setCode(code);
        role.setName(name);
        role.setDescription(description);
        role.setBuiltIn(false);
        role.setCreatedAt(clock.instant());
        role.setUpdatedAt(clock.instant());
        roles.insert(role);
        replacePermissions(role.getId(), permissionIds);
        return details(role.getId());
    }

    @PreAuthorize("hasAuthority('roles:write')")
    @Transactional
    @Audited(
            action = "role.updated",
            targetType = "Role",
            targetIdParameter = 0,
            changedFieldsParameter = 1,
            actorParameter = 2)
    public Map<String, Object> update(long id, Map<String, Object> patch, UserPrincipal actor) {
        RoleEntity role = roles.selectById(id);
        if (role == null) throw ApiException.notFound("Role not found");
        if (Boolean.TRUE.equals(role.getBuiltIn())) throw ApiException.conflict("Built-in roles cannot be changed");
        if (!Set.of("name", "description", "permissionIds").containsAll(patch.keySet()))
            throw ApiException.badRequest("Unsupported role fields");
        if (patch.containsKey("name")) {
            if (!(patch.get("name") instanceof String name) || name.isBlank())
                throw ApiException.badRequest("name must be non-empty");
            role.setName(name);
        }
        if (patch.containsKey("description")) {
            if (patch.get("description") != null && !(patch.get("description") instanceof String))
                throw ApiException.badRequest("description must be a string");
            role.setDescription((String) patch.get("description"));
        }
        role.setUpdatedAt(clock.instant());
        roles.updateById(role);
        if (patch.containsKey("permissionIds")) replacePermissions(id, ids(patch.get("permissionIds")));
        return details(id);
    }

    @PreAuthorize("hasAuthority('roles:write')")
    @Transactional
    @Audited(action = "role.deleted", targetType = "Role", targetIdParameter = 0, actorParameter = 1)
    public void delete(long id, UserPrincipal actor) {
        RoleEntity role = roles.selectById(id);
        if (role == null) throw ApiException.notFound("Role not found");
        if (Boolean.TRUE.equals(role.getBuiltIn())) throw ApiException.conflict("Built-in roles cannot be deleted");
        if (userRoles.selectCount(new LambdaQueryWrapper<UserRoleEntity>().eq(UserRoleEntity::getRoleId, id)) > 0)
            throw ApiException.conflict("Role is assigned to users");
        rolePermissions.delete(new LambdaQueryWrapper<RolePermissionEntity>().eq(RolePermissionEntity::getRoleId, id));
        roles.deleteById(id);
    }

    @PreAuthorize("hasAuthority('user-roles:write')")
    @Transactional
    public void assign(long user, long role, UserPrincipal actor, boolean add) {
        if (!users.exists(user)) throw ApiException.notFound("User not found");
        RoleEntity roleEntity = roles.selectById(role);
        if (roleEntity == null) throw ApiException.notFound("Role not found");
        LambdaQueryWrapper<UserRoleEntity> relation = new LambdaQueryWrapper<UserRoleEntity>()
                .eq(UserRoleEntity::getUserId, user)
                .eq(UserRoleEntity::getRoleId, role);
        if (add) {
            if (userRoles.selectCount(relation) == 0) {
                UserRoleEntity link = new UserRoleEntity();
                link.userId = user;
                link.roleId = role;
                link.assignedBy = actor.userId();
                link.assignedAt = clock.instant();
                userRoles.insert(link);
            }
        } else {
            if ("PlatformAdmin".equals(roleEntity.getCode())) protectAdmin(user, roleEntity);
            userRoles.delete(relation);
        }
        audit.record(
                actor.userId(),
                add ? "user.role.assigned" : "user.role.revoked",
                "User",
                Long.toString(user),
                "SUCCESS",
                null);
    }

    public void assignOnInvitation(long userId, Set<Long> requestedRoleIds, Long actorId) {
        Set<Long> existingRoleIds = roles.selectExistingByIds(requestedRoleIds).stream()
                .map(RoleEntity::getId)
                .collect(java.util.stream.Collectors.toSet());
        if (!existingRoleIds.containsAll(requestedRoleIds)) throw ApiException.badRequest("Unknown role");
        Set<Long> assignedRoleIds = userRoles.selectByUserId(userId).stream()
                .map(link -> link.roleId)
                .collect(java.util.stream.Collectors.toSet());
        Instant assignedAt = clock.instant();
        for (Long roleId : requestedRoleIds) {
            if (assignedRoleIds.contains(roleId)) continue;
            UserRoleEntity link = new UserRoleEntity();
            link.userId = userId;
            link.roleId = roleId;
            link.assignedBy = actorId;
            link.assignedAt = assignedAt;
            userRoles.insert(link);
        }
    }

    public List<String> roleCodesForUser(long userId) {
        return roleCodesForUsers(List.of(userId)).getOrDefault(userId, List.of());
    }

    public Map<Long, List<String>> roleCodesForUsers(java.util.Collection<Long> userIds) {
        if (userIds.isEmpty()) return Map.of();
        List<UserRoleEntity> assignments = userRoles.selectByUserIds(userIds);
        Set<Long> roleIds = assignments.stream().map(link -> link.roleId).collect(java.util.stream.Collectors.toSet());
        Map<Long, String> codeByRoleId = roles.selectExistingByIds(roleIds).stream()
                .collect(java.util.stream.Collectors.toMap(RoleEntity::getId, RoleEntity::getCode));
        Map<Long, List<String>> result = new java.util.HashMap<>();
        for (UserRoleEntity assignment : assignments) {
            String code = codeByRoleId.get(assignment.roleId);
            if (code != null)
                result.computeIfAbsent(assignment.userId, ignored -> new java.util.ArrayList<>())
                        .add(code);
        }
        result.replaceAll((id, codes) -> codes.stream().sorted().toList());
        return result;
    }

    public void ensureNotLastPlatformAdmin(long userId) {
        RoleEntity admin =
                roles.selectOne(new LambdaQueryWrapper<RoleEntity>().eq(RoleEntity::getCode, "PlatformAdmin"));
        if (admin == null) throw new IllegalStateException("PlatformAdmin role is not seeded");
        boolean assigned = userRoles.selectCount(new LambdaQueryWrapper<UserRoleEntity>()
                        .eq(UserRoleEntity::getUserId, userId)
                        .eq(UserRoleEntity::getRoleId, admin.getId()))
                > 0;
        if (assigned) protectAdmin(userId, admin);
    }

    public boolean activePlatformAdminExistsForBootstrap() {
        RoleEntity admin = roles.selectOne(new LambdaQueryWrapper<RoleEntity>()
                .eq(RoleEntity::getCode, "PlatformAdmin")
                .last("FOR UPDATE"));
        if (admin == null) throw new IllegalStateException("PlatformAdmin role is not seeded");
        return activeAdminCount(admin.getId()) > 0;
    }

    public boolean hasPlatformAdminRole(long userId) {
        RoleEntity admin =
                roles.selectOne(new LambdaQueryWrapper<RoleEntity>().eq(RoleEntity::getCode, "PlatformAdmin"));
        return admin != null
                && userRoles.selectCount(new LambdaQueryWrapper<UserRoleEntity>()
                                .eq(UserRoleEntity::getUserId, userId)
                                .eq(UserRoleEntity::getRoleId, admin.getId()))
                        > 0;
    }

    public void assignBootstrapPlatformAdmin(long userId) {
        RoleEntity admin =
                roles.selectOne(new LambdaQueryWrapper<RoleEntity>().eq(RoleEntity::getCode, "PlatformAdmin"));
        if (admin == null) throw new IllegalStateException("PlatformAdmin role is not seeded");
        UserRoleEntity link = new UserRoleEntity();
        link.userId = userId;
        link.roleId = admin.getId();
        link.assignedAt = clock.instant();
        userRoles.insert(link);
    }

    private void replacePermissions(long role, Set<Long> ids) {
        if (!ids.isEmpty()
                && permissions.selectCount(new LambdaQueryWrapper<PermissionEntity>().in(PermissionEntity::getId, ids))
                        != ids.size()) throw ApiException.badRequest("Unknown permission");
        rolePermissions.delete(
                new LambdaQueryWrapper<RolePermissionEntity>().eq(RolePermissionEntity::getRoleId, role));
        for (long permission : ids) {
            RolePermissionEntity link = new RolePermissionEntity();
            link.roleId = role;
            link.permissionId = permission;
            rolePermissions.insert(link);
        }
    }

    private Map<String, Object> details(long id) {
        RoleEntity role = roles.selectById(id);
        if (role == null) throw ApiException.notFound("Role not found");
        Map<String, Object> value = new LinkedHashMap<>(roleView(role));
        List<Long> permissionIds = rolePermissions
                .selectList(new LambdaQueryWrapper<RolePermissionEntity>().eq(RolePermissionEntity::getRoleId, id))
                .stream()
                .map(link -> link.permissionId)
                .toList();
        value.put(
                "permissions",
                permissionIds.isEmpty()
                        ? List.of()
                        : permissions
                                .selectList(new LambdaQueryWrapper<PermissionEntity>()
                                        .in(PermissionEntity::getId, permissionIds)
                                        .orderByAsc(PermissionEntity::getCode))
                                .stream()
                                .map(p -> Map.<String, Object>of("id", p.id, "code", p.code))
                                .toList());
        return value;
    }

    private static Map<String, Object> roleView(RoleEntity role) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", role.getId());
        v.put("code", role.getCode());
        v.put("name", role.getName());
        v.put("description", role.getDescription());
        v.put("builtIn", role.getBuiltIn());
        return v;
    }

    private void protectAdmin(long user, RoleEntity admin) {
        RoleEntity locked = roles.selectOne(new LambdaQueryWrapper<RoleEntity>()
                .eq(RoleEntity::getId, admin.getId())
                .last("FOR UPDATE"));
        List<Long> admins = userRoles
                .selectList(new LambdaQueryWrapper<UserRoleEntity>().eq(UserRoleEntity::getRoleId, locked.getId()))
                .stream()
                .map(link -> link.userId)
                .toList();
        long active = users.countActive(admins);
        if (active <= 1) throw ApiException.conflict("The last active PlatformAdmin cannot be removed");
    }

    private long activeAdminCount(long roleId) {
        List<Long> adminUserIds =
                userRoles
                        .selectList(new LambdaQueryWrapper<UserRoleEntity>().eq(UserRoleEntity::getRoleId, roleId))
                        .stream()
                        .map(link -> link.userId)
                        .toList();
        return users.countActive(adminUserIds);
    }

    private static Set<Long> ids(Object value) {
        if (!(value instanceof List<?> list)) throw ApiException.badRequest("permissionIds must be an array");
        java.util.LinkedHashSet<Long> result = new java.util.LinkedHashSet<>();
        for (Object item : list) {
            if (!(item instanceof Number number)) throw ApiException.badRequest("permissionIds must contain numbers");
            result.add(number.longValue());
        }
        return result;
    }
}
