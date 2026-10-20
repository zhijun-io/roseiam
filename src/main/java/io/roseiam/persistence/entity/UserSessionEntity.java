package io.roseiam.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("user_sessions")
public class UserSessionEntity {
    @TableId(type = IdType.AUTO)
    public Long id;

    public Long userId;
    public String tokenHash;
    public Instant createdAt;
    public Instant lastActivityAt;
    public Instant idleExpiresAt;
    public Instant absoluteExpiresAt;
    public Instant revokedAt;

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

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String value) {
        this.tokenHash = value;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant value) {
        this.createdAt = value;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public void setLastActivityAt(Instant value) {
        this.lastActivityAt = value;
    }

    public Instant getIdleExpiresAt() {
        return idleExpiresAt;
    }

    public void setIdleExpiresAt(Instant value) {
        this.idleExpiresAt = value;
    }

    public Instant getAbsoluteExpiresAt() {
        return absoluteExpiresAt;
    }

    public void setAbsoluteExpiresAt(Instant value) {
        this.absoluteExpiresAt = value;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant value) {
        this.revokedAt = value;
    }
}
