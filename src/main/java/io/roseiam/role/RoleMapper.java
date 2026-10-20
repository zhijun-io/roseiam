package io.roseiam.role;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.Collection;
import java.util.List;

public interface RoleMapper extends BaseMapper<RoleEntity> {
    default List<RoleEntity> selectExistingByIds(Collection<Long> ids) {
        return ids.isEmpty() ? List.of() : selectBatchIds(ids);
    }
}
