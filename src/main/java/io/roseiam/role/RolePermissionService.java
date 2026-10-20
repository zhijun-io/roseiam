package io.roseiam.role;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class RolePermissionService {

    private final UserRoleMapper userRoles;
    private final RolePermissionMapper rolePermissions;
    private final PermissionMapper permissions;
    private final RoleMapper roles;

    public RolePermissionService(
            UserRoleMapper userRoles,
            RolePermissionMapper rolePermissions,
            PermissionMapper permissions,
            RoleMapper roles) {
        this.userRoles = userRoles;
        this.rolePermissions = rolePermissions;
        this.permissions = permissions;
        this.roles = roles;
    }

    public Set<String> permissions(Long userId) {
        Set<Long> roleIds = roleIds(userId);
        if (roleIds.isEmpty()) return Set.of();
        Set<Long> permissionIds = rolePermissions
                .selectList(new LambdaQueryWrapper<RolePermissionEntity>().in(RolePermissionEntity::getRoleId, roleIds))
                .stream()
                .map(row -> row.permissionId)
                .collect(java.util.stream.Collectors.toSet());
        if (permissionIds.isEmpty()) return Set.of();
        return new LinkedHashSet<>(
                permissions
                        .selectList(
                                new LambdaQueryWrapper<PermissionEntity>().in(PermissionEntity::getId, permissionIds))
                        .stream()
                        .map(row -> row.code)
                        .toList());
    }

    public Set<String> roles(Long userId) {
        Set<Long> roleIds = roleIds(userId);
        return roleIds.isEmpty()
                ? Set.of()
                : new LinkedHashSet<>(
                        roles.selectList(new LambdaQueryWrapper<RoleEntity>().in(RoleEntity::getId, roleIds)).stream()
                                .map(RoleEntity::getCode)
                                .toList());
    }

    private Set<Long> roleIds(Long userId) {
        return userRoles
                .selectList(new LambdaQueryWrapper<UserRoleEntity>().eq(UserRoleEntity::getUserId, userId))
                .stream()
                .map(row -> row.roleId)
                .collect(java.util.stream.Collectors.toSet());
    }
}
