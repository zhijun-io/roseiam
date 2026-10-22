package io.roseiam.role;

import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class RolePermissionService {

    private final UserRoleMapper userRoles;

    public RolePermissionService(UserRoleMapper userRoles) {
        this.userRoles = userRoles;
    }

    public Set<String> permissions(Long userId) {
        return new LinkedHashSet<>(userRoles.selectPermissionCodesByUserId(userId));
    }

    public Set<String> roles(Long userId) {
        return new LinkedHashSet<>(userRoles.selectRoleCodesByUserId(userId));
    }
}
