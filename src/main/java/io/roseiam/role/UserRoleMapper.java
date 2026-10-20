package io.roseiam.role;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.Collection;
import java.util.List;

public interface UserRoleMapper extends BaseMapper<UserRoleEntity> {
    default List<UserRoleEntity> selectByUserIds(Collection<Long> userIds) {
        return userIds.isEmpty()
                ? List.of()
                : selectList(new LambdaQueryWrapper<UserRoleEntity>().in(UserRoleEntity::getUserId, userIds));
    }

    default List<UserRoleEntity> selectByUserId(Long userId) {
        return selectByUserIds(List.of(userId));
    }
}
