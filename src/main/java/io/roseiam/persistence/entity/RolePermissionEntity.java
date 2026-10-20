package io.roseiam.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("role_permissions")
public class RolePermissionEntity {
    @TableId
    public Long roleId;

    public Long permissionId;

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long value) {
        this.roleId = value;
    }

    public Long getPermissionId() {
        return permissionId;
    }

    public void setPermissionId(Long value) {
        this.permissionId = value;
    }
}
