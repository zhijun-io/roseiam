package io.roseiam.role;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserRoleMapper extends BaseMapper<UserRoleEntity> {
    @Select("""
            SELECT DISTINCT p.code
            FROM user_roles ur
            JOIN roles r ON r.id = ur.role_id
            JOIN role_permissions rp ON rp.role_id = r.id
            JOIN permissions p ON p.id = rp.permission_id
            WHERE ur.user_id = #{userId}
            ORDER BY p.code
            """)
    List<String> selectPermissionCodesByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT DISTINCT r.code
            FROM user_roles ur
            JOIN roles r ON r.id = ur.role_id
            WHERE ur.user_id = #{userId}
            ORDER BY r.code
            """)
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT COUNT(*)
            FROM user_roles ur
            JOIN users u ON u.id = ur.user_id
            WHERE ur.role_id = #{roleId}
              AND u.status = 'ACTIVE'
            """)
    long countActiveUsersByRoleId(@Param("roleId") Long roleId);

    default List<UserRoleEntity> selectByUserIds(Collection<Long> userIds) {
        return userIds.isEmpty()
                ? List.of()
                : selectList(new LambdaQueryWrapper<UserRoleEntity>().in(UserRoleEntity::getUserId, userIds));
    }

    default List<UserRoleEntity> selectByUserId(Long userId) {
        return selectByUserIds(List.of(userId));
    }
}
