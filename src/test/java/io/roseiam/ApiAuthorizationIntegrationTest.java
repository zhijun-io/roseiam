package io.roseiam;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.roseiam.audit.AuditEventEntity;
import io.roseiam.audit.AuditMapper;
import io.roseiam.identity.AuthDtos;
import io.roseiam.platformsetting.MailDeliveryService;
import io.roseiam.role.RoleService;
import io.roseiam.shared.UserPrincipal;
import io.roseiam.user.UserEntity;
import io.roseiam.user.UserMapper;
import io.roseiam.user.UserService;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ApiAuthorizationIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserService users;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoleService roles;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwords;

    @Autowired
    private AuditMapper audit;

    @MockitoBean
    private MailDeliveryService mail;

    @Autowired
    private tools.jackson.databind.json.JsonMapper jsonMapper;

    @Test
    void authenticatedAdminCanUseProtectedApisAndAuditQueriesAreRecorded() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());

        createAdmin("api-admin@example.test");
        AuthDtos.LoginResponse login = login("api-admin@example.test", "test-pass");
        String bearer = "Bearer " + login.accessToken();

        mvc.perform(get("/api/me").header("Authorization", bearer)).andExpect(status().isOk());
        mvc.perform(patch("/api/me")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated Admin\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/audit-events").header("Authorization", bearer).param("action", "user.updated"))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(audit.selectCount(
                        new LambdaQueryWrapper<AuditEventEntity>().eq(AuditEventEntity::getAction, "user.updated")))
                .isEqualTo(1);
        AuditEventEntity queryEvent = audit.selectOne(
                new LambdaQueryWrapper<AuditEventEntity>().eq(AuditEventEntity::getAction, "audit.events.queried"));
        org.assertj.core.api.Assertions.assertThat(queryEvent).isNotNull();
    }

    @Test
    void invitationResponseDoesNotWaitForOrDependOnAsyncMailDelivery() throws Exception {
        createAdmin("mail-admin@example.test");
        String bearer =
                "Bearer " + login("mail-admin@example.test", "test-pass").accessToken();
        String recipient = "invited@example.test";
        doThrow(new org.springframework.mail.MailSendException("SMTP unavailable"))
                .when(mail)
                .sendInvitation(eq(recipient), anyString());

        mvc.perform(post("/api/user-invitations")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"invited@example.test\",\"roleIds\":[]}"))
                .andExpect(status().isCreated());

        verify(mail, timeout(5000)).sendInvitation(eq(recipient), anyString());
    }

    @Test
    void auditedServiceChangeRollsBackWithItsAuditEvent() {
        UserEntity admin = createAdmin("audit-rollback@example.test");
        UserPrincipal principal = new UserPrincipal(admin.getId(), admin.getEmail(), Set.of(), Set.of());
        long before = audit.selectCount(
                new LambdaQueryWrapper<AuditEventEntity>().eq(AuditEventEntity::getAction, "user.updated"));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            users.updateOwnName(admin.getId(), "Rolled Back Name", principal);
            status.setRollbackOnly();
        });

        UserEntity unchanged = userMapper.selectById(admin.getId());
        org.assertj.core.api.Assertions.assertThat(unchanged.getName()).isEqualTo("Integration Admin");
        org.assertj.core.api.Assertions.assertThat(audit.selectCount(
                        new LambdaQueryWrapper<AuditEventEntity>().eq(AuditEventEntity::getAction, "user.updated")))
                .isEqualTo(before);
    }

    private AuthDtos.LoginResponse login(String email, String password) throws Exception {
        var result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new AuthDtos.LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();
        return jsonMapper.readValue(result.getResponse().getContentAsByteArray(), AuthDtos.LoginResponse.class);
    }

    private UserEntity createAdmin(String email) {
        Instant now = Instant.now();
        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setEmailVerifiedAt(now);
        user.setPasswordHash(passwords.encode("test-pass"));
        user.setName("Integration Admin");
        user.setStatus("ACTIVE");
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);
        roles.assignBootstrapPlatformAdmin(user.getId());
        return user;
    }
}
