package io.roseiam;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.roseiam.persistence.entity.PermissionEntity;
import io.roseiam.persistence.mapper.PermissionMapper;
import io.roseiam.role.RoleMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SchemaConventionIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RoleMapper roles;

    @Autowired
    private PermissionMapper permissions;

    @Test
    void flywayCreatesLongPrimaryKeysAndNoForeignKeyConstraints() {
        Integer userTableCount = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = 'users'",
                Integer.class);
        Integer primaryKeyTypeCount = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'users' AND column_name = 'id' AND data_type = 'bigint' AND is_identity = 'YES'",
                Integer.class);
        Integer foreignKeyCount = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.table_constraints WHERE constraint_schema = current_schema() AND constraint_type = 'FOREIGN KEY'",
                Integer.class);

        assertThat(userTableCount).isEqualTo(1);
        assertThat(primaryKeyTypeCount).isEqualTo(1);
        assertThat(foreignKeyCount).isZero();
    }

    @Test
    void mybatisPlusMapperQueriesAndBaseMapperCrudWork() {
        assertThat(permissions.selectList(new LambdaQueryWrapper<PermissionEntity>()).stream()
                        .map(PermissionEntity::getCode))
                .contains("users:read", "roles:read", "audit-events:read");
        assertThat(roles.selectById(1L)).isNotNull();
    }
}
