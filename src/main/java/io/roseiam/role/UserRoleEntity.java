package io.roseiam.role;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("user_roles")
public class UserRoleEntity {
    @TableId
    public Long userId;

    public Long roleId;
    public Long assignedBy;
    public Instant assignedAt;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long value) {
        this.userId = value;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long value) {
        this.roleId = value;
    }

    public Long getAssignedBy() {
        return assignedBy;
    }

    public void setAssignedBy(Long value) {
        this.assignedBy = value;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(Instant value) {
        this.assignedAt = value;
    }
}
