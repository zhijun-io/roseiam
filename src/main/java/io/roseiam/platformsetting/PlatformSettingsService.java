package io.roseiam.platformsetting;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.roseiam.audit.AuditService;
import io.roseiam.config.ApiException;
import io.roseiam.shared.UserPrincipal;
import java.net.URI;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class PlatformSettingsService {

    private static final Set<String> GENERAL_FIELDS = Set.of("platformName", "defaultLocale", "frontendBaseUrl");
    private static final Set<String> MAIL_FIELDS = Set.of(
            "smtpHost",
            "smtpPort",
            "smtpUsername",
            "smtpFromAddress",
            "smtpAuthEnabled",
            "smtpStartTlsEnabled",
            "smtpPassword",
            "clearSmtpPassword");

    private final PlatformSettingMapper mapper;
    private final JsonMapper jsonMapper;
    private final AuditService audit;
    private final Clock clock;
    private final String encryptionKey;
    private final String initialFrontendUrl;
    private final String initialMailHost;
    private final int initialMailPort;

    public PlatformSettingsService(
            PlatformSettingMapper mapper,
            JsonMapper jsonMapper,
            AuditService audit,
            Clock clock,
            @Value("${app.smtp.encryption-key:}") String encryptionKey,
            @Value("${app.frontend-base-url:http://localhost:3000}") String initialFrontendUrl,
            @Value("${spring.mail.host:localhost}") String initialMailHost,
            @Value("${spring.mail.port:1025}") int initialMailPort) {
        this.mapper = mapper;
        this.jsonMapper = jsonMapper;
        this.audit = audit;
        this.clock = clock;
        this.encryptionKey = encryptionKey;
        this.initialFrontendUrl = initialFrontendUrl;
        this.initialMailHost = initialMailHost;
        this.initialMailPort = initialMailPort;
    }

    @Transactional
    public void ensureDefaults() {
        Map<String, Object> general =
                Map.of("platformName", "RoseIAM", "defaultLocale", "zh-CN", "frontendBaseUrl", initialFrontendUrl);
        Map<String, Object> mail = new LinkedHashMap<>();
        mail.put("smtpHost", initialMailHost);
        mail.put("smtpPort", initialMailPort);
        mail.put("smtpUsername", "");
        mail.put("smtpFromAddress", "no-reply@roseiam.local");
        mail.put("smtpAuthEnabled", false);
        mail.put("smtpStartTlsEnabled", false);
        putIfMissing("general", general);
        putIfMissing("mail", mail);
    }

    @PreAuthorize("hasAuthority('platform-settings:read')")
    @Transactional(readOnly = true)
    public Map<String, Object> getSettings() {
        Map<String, Object> mail = publicMail(read("mail"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("general", read("general"));
        result.put("mail", mail);
        return result;
    }

    @PreAuthorize("hasAuthority('platform-settings:write')")
    @Transactional
    public Map<String, Object> update(Map<String, Object> patch, UserPrincipal actor) {
        rejectUnknown(patch.keySet(), Set.of("general", "mail"));
        if (patch.isEmpty()) {
            throw ApiException.badRequest("At least one settings group is required");
        }
        List<String> changedFields = new ArrayList<>();
        if (patch.containsKey("general")) {
            Map<String, Object> changes = asMap(patch.get("general"), "general");
            rejectUnknown(changes.keySet(), GENERAL_FIELDS);
            Map<String, Object> current = read("general");
            Map<String, Object> updated = new LinkedHashMap<>(current);
            changes.forEach((key, value) -> {
                validateGeneral(key, value);
                if (!java.util.Objects.equals(current.get(key), value)) {
                    changedFields.add("general." + key);
                }
                updated.put(key, value);
            });
            write("general", updated);
        }
        if (patch.containsKey("mail")) {
            Map<String, Object> changes = asMap(patch.get("mail"), "mail");
            rejectUnknown(changes.keySet(), MAIL_FIELDS);
            Map<String, Object> current = read("mail");
            Map<String, Object> updated = new LinkedHashMap<>(current);
            boolean clearPassword = Boolean.TRUE.equals(changes.get("clearSmtpPassword"));
            for (Map.Entry<String, Object> entry : changes.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (key.equals("clearSmtpPassword")) {
                    if (clearPassword
                            && Boolean.TRUE.equals(
                                    changes.getOrDefault("smtpAuthEnabled", current.get("smtpAuthEnabled")))) {
                        throw ApiException.badRequest(
                                "SMTP password can only be cleared while authentication is disabled");
                    }
                    continue;
                }
                if (key.equals("smtpPassword")) {
                    if (!(value instanceof String password) || password.isBlank()) {
                        throw ApiException.badRequest("SMTP password must be non-empty when supplied");
                    }
                    String encrypted = encrypt(password);
                    if (!java.util.Objects.equals(updated.get("smtpPasswordEncrypted"), encrypted)) {
                        changedFields.add("mail.smtpPassword");
                    }
                    updated.put("smtpPasswordEncrypted", encrypted);
                    continue;
                }
                validateMail(key, value);
                if (!java.util.Objects.equals(current.get(key), value)) {
                    changedFields.add("mail." + key);
                }
                updated.put(key, value);
            }
            if (clearPassword) {
                if (updated.containsKey("smtpPasswordEncrypted")) {
                    changedFields.add("mail.smtpPassword");
                }
                updated.remove("smtpPasswordEncrypted");
            }
            if (Boolean.TRUE.equals(updated.get("smtpAuthEnabled"))) {
                if (string(updated.get("smtpUsername")).isBlank()
                        || !updated.containsKey("smtpPasswordEncrypted")
                        || encryptionKey.isBlank()) {
                    throw ApiException.badRequest(
                            "SMTP authentication requires username, encrypted password and deployment key");
                }
            }
            write("mail", updated);
        }
        if (!changedFields.isEmpty()) {
            audit.record(
                    actor.userId(),
                    "platform.settings.updated",
                    "PlatformSetting",
                    null,
                    "SUCCESS",
                    Map.of("changedFields", changedFields));
        }
        return getSettingsWithoutAuthorization();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> generalForMail() {
        return read("general");
    }

    @Transactional(readOnly = true)
    public Map<String, Object> mailForDelivery() {
        return read("mail");
    }

    public String decryptPassword(Map<String, Object> mail) {
        Object encrypted = mail.get("smtpPasswordEncrypted");
        return encrypted == null ? "" : decrypt(encrypted.toString());
    }

    private Map<String, Object> getSettingsWithoutAuthorization() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("general", read("general"));
        result.put("mail", publicMail(read("mail")));
        return result;
    }

    private Map<String, Object> publicMail(Map<String, Object> stored) {
        Map<String, Object> visible = new LinkedHashMap<>(stored);
        boolean configured = visible.remove("smtpPasswordEncrypted") != null;
        visible.put("smtpPasswordConfigured", configured);
        return visible;
    }

    private void putIfMissing(String type, Map<String, Object> value) {
        if (mapper.selectCount(new LambdaQueryWrapper<PlatformSettingEntity>().eq(PlatformSettingEntity::getType, type))
                == 0) {
            PlatformSettingEntity setting = new PlatformSettingEntity();
            setting.type = type;
            setting.value = jsonMapper.writeValueAsString(value);
            setting.updatedAt = clock.instant();
            mapper.insert(setting);
        }
    }

    private Map<String, Object> read(String type) {
        PlatformSettingEntity setting = mapper.selectOne(
                new LambdaQueryWrapper<PlatformSettingEntity>().eq(PlatformSettingEntity::getType, type));
        if (setting == null) throw new IllegalStateException("Platform setting defaults have not been initialized");
        return jsonMapper.readValue(setting.value, new tools.jackson.core.type.TypeReference<Map<String, Object>>() {});
    }

    private void write(String type, Map<String, Object> value) {
        PlatformSettingEntity setting = mapper.selectOne(
                new LambdaQueryWrapper<PlatformSettingEntity>().eq(PlatformSettingEntity::getType, type));
        if (setting == null) throw new IllegalStateException("Platform setting defaults have not been initialized");
        setting.value = jsonMapper.writeValueAsString(value);
        setting.updatedAt = clock.instant();
        mapper.updateById(setting);
    }

    private void validateGeneral(String key, Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw ApiException.badRequest(key + " must be a non-empty string");
        }
        if (key.equals("platformName") && text.length() > 100) {
            throw ApiException.badRequest("platformName is too long");
        }
        if (key.equals("defaultLocale")) {
            try {
                new java.util.Locale.Builder().setLanguageTag(text).build();
            } catch (java.util.IllformedLocaleException exception) {
                throw ApiException.badRequest("defaultLocale must be a valid BCP 47 language tag");
            }
        }
        if (key.equals("frontendBaseUrl")) {
            URI uri;
            try {
                uri = URI.create(text);
            } catch (IllegalArgumentException exception) {
                throw ApiException.badRequest("frontendBaseUrl must be an absolute HTTP(S) URL");
            }
            if (!uri.isAbsolute()
                    || uri.getHost() == null
                    || !List.of("http", "https").contains(uri.getScheme())
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null) {
                throw ApiException.badRequest("frontendBaseUrl must not contain userinfo, query or fragment");
            }
        }
    }

    private void validateMail(String key, Object value) {
        switch (key) {
            case "smtpHost", "smtpUsername", "smtpFromAddress" -> {
                if (!(value instanceof String text) || text.isBlank()) {
                    throw ApiException.badRequest(key + " must be a non-empty string");
                }
                if (key.equals("smtpFromAddress") && !text.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                    throw ApiException.badRequest("smtpFromAddress must be an email address");
                }
            }
            case "smtpPort" -> {
                if (!(value instanceof Number number) || number.intValue() < 1 || number.intValue() > 65535) {
                    throw ApiException.badRequest("smtpPort must be between 1 and 65535");
                }
            }
            case "smtpAuthEnabled", "smtpStartTlsEnabled" -> {
                if (!(value instanceof Boolean)) {
                    throw ApiException.badRequest(key + " must be boolean");
                }
            }
            default -> throw ApiException.badRequest("Unsupported SMTP setting");
        }
    }

    private String encrypt(String plaintext) {
        try {
            byte[] key = java.util.Base64.getDecoder().decode(encryptionKey);
            if (key.length != 32) {
                throw ApiException.badRequest("SMTP encryption key must decode to 32 bytes");
            }
            byte[] nonce = new byte[12];
            new java.security.SecureRandom().nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] payload = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, payload, 0, nonce.length);
            System.arraycopy(encrypted, 0, payload, nonce.length, encrypted.length);
            return "v1:" + java.util.Base64.getEncoder().encodeToString(payload);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw ApiException.badRequest("SMTP encryption key is missing or invalid");
        }
    }

    private String decrypt(String ciphertext) {
        try {
            byte[] key = java.util.Base64.getDecoder().decode(encryptionKey);
            byte[] payload = java.util.Base64.getDecoder().decode(ciphertext.substring(3));
            byte[] nonce = java.util.Arrays.copyOfRange(payload, 0, 12);
            byte[] encrypted = java.util.Arrays.copyOfRange(payload, 12, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            return new String(cipher.doFinal(encrypted), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("SMTP password could not be decrypted with the configured key");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value, String group) {
        if (!(value instanceof Map<?, ?> map)) {
            throw ApiException.badRequest(group + " must be an object");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, entry) -> {
            if (!(key instanceof String text)) {
                throw ApiException.badRequest(group + " contains an invalid field");
            }
            result.put(text, entry);
        });
        return result;
    }

    private void rejectUnknown(Set<String> supplied, Set<String> allowed) {
        if (!allowed.containsAll(supplied)) {
            throw ApiException.badRequest("Request contains unsupported fields");
        }
    }

    private String string(Object value) {
        return value == null ? "" : value.toString();
    }
}
