package io.roseiam.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.Instant;
import java.util.Collection;
import org.springframework.stereotype.Service;

@Service
public class UserDirectoryService {
    private final UserMapper users;

    public UserDirectoryService(UserMapper users) {
        this.users = users;
    }

    public UserAccount findByEmail(String email) {
        return toAccount(users.selectOne(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getEmail, email)));
    }

    public UserAccount findVerifiedByEmail(String email) {
        return toAccount(users.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getEmail, email)
                .isNotNull(UserEntity::getEmailVerifiedAt)));
    }

    public UserAccount findById(long id) {
        return toAccount(users.selectById(id));
    }

    public boolean exists(long id) {
        return users.selectCount(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getId, id)) > 0;
    }

    public long countActive(Collection<Long> ids) {
        if (ids.isEmpty()) return 0;
        return users.selectCount(
                new LambdaQueryWrapper<UserEntity>().in(UserEntity::getId, ids).eq(UserEntity::getStatus, "ACTIVE"));
    }

    public void updatePassword(long id, String passwordHash, Instant updatedAt) {
        users.update(
                null,
                new LambdaUpdateWrapper<UserEntity>()
                        .eq(UserEntity::getId, id)
                        .set(UserEntity::getPasswordHash, passwordHash)
                        .set(UserEntity::getUpdatedAt, updatedAt));
    }

    private static UserAccount toAccount(UserEntity user) {
        return user == null
                ? null
                : new UserAccount(
                        user.getId(),
                        user.getEmail(),
                        user.getPasswordHash(),
                        user.getEmailVerifiedAt(),
                        user.getStatus());
    }

    public record UserAccount(Long id, String email, String passwordHash, Instant emailVerifiedAt, String status) {}
}
