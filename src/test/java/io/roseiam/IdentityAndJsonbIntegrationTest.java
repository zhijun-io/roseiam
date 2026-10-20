package io.roseiam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.roseiam.identity.AuthDtos;
import io.roseiam.identity.TokenService;
import io.roseiam.identity.UserSessionEntity;
import io.roseiam.identity.UserSessionMapper;
import io.roseiam.platformsetting.PlatformSettingEntity;
import io.roseiam.platformsetting.PlatformSettingMapper;
import io.roseiam.role.UserRoleEntity;
import io.roseiam.role.UserRoleMapper;
import io.roseiam.shared.UserPrincipal;
import io.roseiam.user.UserEntity;
import io.roseiam.user.UserInvitationEntity;
import io.roseiam.user.UserInvitationMapper;
import io.roseiam.user.UserMapper;
import io.roseiam.user.UserService;
import io.roseiam.user.UserSettingService;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IdentityAndJsonbIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserMapper users;

    @Autowired
    private UserSessionMapper sessions;

    @Autowired
    private PlatformSettingMapper platformSettings;

    @Autowired
    private UserSettingService userSettings;

    @Autowired
    private PasswordEncoder passwords;

    @Autowired
    private TokenService tokens;

    @Autowired
    private tools.jackson.databind.json.JsonMapper jsonMapper;

    @Autowired
    private UserService userLifecycle;

    @Autowired
    private UserInvitationMapper invitations;

    @Autowired
    private UserRoleMapper userRoles;

    @Test
    void loginFilterPersistsOpaqueSessionAndLogoutFilterRevokesIt() throws Exception {
        UserEntity user = activeUser("login");

        AuthDtos.LoginResponse response = login(user.getEmail(), "test-pass");

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.accessToken()).isNotBlank();
        UserSessionEntity session = sessions.selectOne(new LambdaQueryWrapper<UserSessionEntity>()
                .eq(UserSessionEntity::getTokenHash, tokens.hash(response.accessToken())));
        assertThat(session).isNotNull();
        assertThat(session.getUserId()).isEqualTo(user.getId());
        assertThat(session.getTokenHash()).isNotEqualTo(response.accessToken());

        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + response.accessToken()))
                .andExpect(status().isNoContent());
        assertThat(sessions.selectById(session.getId()).getRevokedAt()).isNotNull();
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + response.accessToken()))
                .andExpect(status().isNoContent());
    }

    @Test
    void loginFilterRejectsWrongPassword() throws Exception {
        UserEntity user = activeUser("wrong-password");

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new AuthDtos.LoginRequest(user.getEmail(), "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void loginFilterRejectsUnverifiedAccountBeforeSessionCreation() throws Exception {
        UserEntity user = new UserEntity();
        user.setEmail("unverified@example.test");
        user.setPasswordHash(passwords.encode("test-pass"));
        user.setStatus("ACTIVE");
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        users.insert(user);

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                jsonMapper.writeValueAsString(new AuthDtos.LoginRequest(user.getEmail(), "test-pass"))))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(sessions.selectCount(
                        new LambdaQueryWrapper<UserSessionEntity>().eq(UserSessionEntity::getUserId, user.getId())))
                .isZero();
    }

    @Test
    void postgresJsonbTypeHandlerBindsAndReadsStoredValues() {
        PlatformSettingEntity setting = new PlatformSettingEntity();
        setting.type = "integration-test";
        setting.value = "{\"enabled\":true,\"count\":2}";
        setting.updatedAt = Instant.now();
        platformSettings.insert(setting);

        PlatformSettingEntity stored = platformSettings.selectOne(
                new LambdaQueryWrapper<PlatformSettingEntity>().eq(PlatformSettingEntity::getType, "integration-test"));

        assertThat(jsonMapper.readValue(
                        stored.value, new tools.jackson.core.type.TypeReference<Map<String, Object>>() {}))
                .isEqualTo(Map.of("enabled", true, "count", 2));
    }

    @Test
    void preferencesAreStoredAsTypedJsonValueAndReadByOwner() {
        UserEntity user = activeUser("preferences");
        UserPrincipal principal = new UserPrincipal(user.getId(), user.getEmail(), Set.of(), Set.of());
        Map<String, Object> preferences = Map.of("locale", "en-US", "timeZone", "UTC", "theme", "dark");

        assertThat(userSettings.replace(principal, preferences)).isEqualTo(preferences);
        assertThat(userSettings.get(principal)).isEqualTo(preferences);
    }

    @Test
    void bootstrapInvitationCanBeRetriedWithoutCreatingDuplicateUsersOrRoles() {
        String email = "bootstrap@example.test";

        io.roseiam.user.UserDtos.CreatedInvitation first = userLifecycle.createBootstrapInvitation(email);
        io.roseiam.user.UserDtos.CreatedInvitation second = userLifecycle.createBootstrapInvitation(email);

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(second.delivery().token()).isNotEqualTo(first.delivery().token());
        UserEntity user = users.selectOne(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getEmail, email));
        assertThat(user).isNotNull();
        assertThat(userRoles.selectCount(
                        new LambdaQueryWrapper<UserRoleEntity>().eq(UserRoleEntity::getUserId, user.getId())))
                .isEqualTo(1);
        assertThat(invitations.selectCount(new LambdaQueryWrapper<UserInvitationEntity>()
                        .eq(UserInvitationEntity::getUserId, user.getId())
                        .isNull(UserInvitationEntity::getRevokedAt)))
                .isEqualTo(1);
    }

    private UserEntity activeUser(String suffix) {
        UserEntity user = new UserEntity();
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        user.setEmail(suffix + "@example.test");
        user.setEmailVerifiedAt(createdAt);
        user.setPasswordHash(passwords.encode("test-pass"));
        user.setName("Test User");
        user.setStatus("ACTIVE");
        user.setCreatedAt(createdAt);
        user.setUpdatedAt(createdAt);
        users.insert(user);
        return user;
    }

    private AuthDtos.LoginResponse login(String email, String password) throws Exception {
        var result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new AuthDtos.LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();
        return jsonMapper.readValue(result.getResponse().getContentAsByteArray(), AuthDtos.LoginResponse.class);
    }
}
