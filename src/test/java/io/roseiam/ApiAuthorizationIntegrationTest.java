package io.roseiam;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.roseiam.audit.AuditMapper;
import io.roseiam.config.JsonCodec;
import io.roseiam.identity.AuthDtos;
import io.roseiam.identity.IdentityService;
import io.roseiam.persistence.entity.AuditEventEntity;
import io.roseiam.user.UserLifecycleService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ApiAuthorizationIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserLifecycleService users;

    @Autowired
    private IdentityService identity;

    @Autowired
    private AuditMapper audit;

    @Autowired
    private JsonCodec json;

    @Test
    void authenticatedAdminCanUseProtectedApisAndAuditQueriesAreRecorded() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());

        AuthDtos.CreatedInvitation invitation = users.createBootstrapInvitation("api-admin@example.test");
        users.accept(new AuthDtos.InvitationAcceptance(invitation.delivery().token(), "test-pass", "API Admin"));
        AuthDtos.LoginResponse login = identity.login(new AuthDtos.LoginRequest("api-admin@example.test", "test-pass"));
        String bearer = "Bearer " + login.accessToken();

        mvc.perform(get("/api/me").header("Authorization", bearer)).andExpect(status().isOk());
        mvc.perform(patch("/api/me")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated Admin\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/audit-events").header("Authorization", bearer).param("action", "user.updated"))
                .andExpect(status().isOk());

        AuditEventEntity queryEvent = audit.selectOne(
                new LambdaQueryWrapper<AuditEventEntity>().eq(AuditEventEntity::getAction, "audit.events.queried"));
        Map<String, Object> metadata = json.readMap(queryEvent.safeMetadata);
        org.assertj.core.api.Assertions.assertThat(metadata.get("filtersUsed")).isEqualTo(java.util.List.of("action"));
        org.assertj.core.api.Assertions.assertThat(queryEvent.safeMetadata).doesNotContain("user.updated");
    }
}
