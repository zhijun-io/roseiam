package io.roseiam.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.roseiam.persistence.JsonbStringTypeHandler;
import java.time.Instant;

@TableName(value = "audit_events", autoResultMap = true)
public class AuditEventEntity {
    @TableId(type = IdType.AUTO)
    public Long id;

    public Instant occurredAt;
    public Long actorUserId;
    public String action;
    public String targetType;
    public String targetId;
    public String result;
    public String requestId;

    @TableField(value = "safe_metadata", typeHandler = JsonbStringTypeHandler.class)
    public String safeMetadata;

    public Long getId() {
        return id;
    }

    public void setId(Long value) {
        this.id = value;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant value) {
        this.occurredAt = value;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(Long value) {
        this.actorUserId = value;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String value) {
        this.action = value;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String value) {
        this.targetType = value;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String value) {
        this.targetId = value;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String value) {
        this.result = value;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String value) {
        this.requestId = value;
    }

    public String getSafeMetadata() {
        return safeMetadata;
    }

    public void setSafeMetadata(String value) {
        this.safeMetadata = value;
    }
}
