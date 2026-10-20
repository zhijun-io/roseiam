package io.roseiam.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.roseiam.config.ApiException;
import io.roseiam.config.JsonCodec;
import io.roseiam.identity.UserPrincipal;
import io.roseiam.persistence.entity.UserSettingEntity;
import io.roseiam.user.mapper.UserSettingMapper;
import java.time.Clock;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserSettingService {
    private final UserSettingMapper mapper;
    private final JsonCodec json;
    private final Clock clock;

    public UserSettingService(UserSettingMapper mapper, JsonCodec json, Clock clock) {
        this.mapper = mapper;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UserPrincipal user) {
        return read(user.userId());
    }

    @Transactional
    public Map<String, Object> replace(UserPrincipal user, Map<String, Object> value) {
        if (!value.keySet().equals(Set.of("locale", "timeZone", "theme")))
            throw ApiException.badRequest("locale, timeZone and theme are required; unknown fields are not allowed");
        try {
            new Locale.Builder().setLanguageTag(text(value, "locale")).build();
        } catch (RuntimeException e) {
            throw ApiException.badRequest("locale must be a valid language tag");
        }
        try {
            ZoneId.of(text(value, "timeZone"));
        } catch (RuntimeException e) {
            throw ApiException.badRequest("timeZone must be a valid IANA zone");
        }
        if (!Set.of("light", "dark", "system").contains(text(value, "theme")))
            throw ApiException.badRequest("theme must be light, dark or system");
        String data = json.write(value);
        UserSettingEntity setting = mapper.selectOne(new LambdaQueryWrapper<UserSettingEntity>()
                .eq(UserSettingEntity::getUserId, user.userId())
                .eq(UserSettingEntity::getType, "preferences"));
        if (setting == null) {
            setting = new UserSettingEntity();
            setting.userId = user.userId();
            setting.type = "preferences";
        }
        setting.value = data;
        setting.updatedAt = clock.instant();
        if (setting.id == null) mapper.insert(setting);
        else mapper.updateById(setting);
        return new LinkedHashMap<>(value);
    }

    private Map<String, Object> read(long id) {
        UserSettingEntity setting = mapper.selectOne(new LambdaQueryWrapper<UserSettingEntity>()
                .eq(UserSettingEntity::getUserId, id)
                .eq(UserSettingEntity::getType, "preferences"));
        String data = setting == null ? null : setting.value;
        return (data == null
                        ? java.util.Optional.<Map<String, Object>>empty()
                        : java.util.Optional.of(json.readMap(data)))
                .orElseGet(() ->
                        new LinkedHashMap<>(Map.of("locale", "zh-CN", "timeZone", "Asia/Shanghai", "theme", "system")));
    }

    private static String text(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof String text) || text.isBlank())
            throw ApiException.badRequest(key + " must be a non-empty string");
        return text;
    }
}
