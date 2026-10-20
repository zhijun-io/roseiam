package io.roseiam.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.roseiam.shared.JsonbStringTypeHandler;
import java.time.Instant;

@TableName(value = "user_settings", autoResultMap = true)
public class UserSettingEntity {
    @TableId(type = IdType.AUTO)
    public Long id;

    public Long userId;
    public String type;

    @TableField(typeHandler = JsonbStringTypeHandler.class)
    public String value;

    public Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long value) {
        this.id = value;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long value) {
        this.userId = value;
    }

    public String getType() {
        return type;
    }

    public void setType(String value) {
        this.type = value;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant value) {
        this.updatedAt = value;
    }
}
