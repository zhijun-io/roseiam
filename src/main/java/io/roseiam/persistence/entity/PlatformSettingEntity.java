package io.roseiam.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.roseiam.persistence.JsonbStringTypeHandler;
import java.time.Instant;

@TableName(value = "platform_settings", autoResultMap = true)
public class PlatformSettingEntity {
    @TableId(type = IdType.AUTO)
    public Long id;

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
