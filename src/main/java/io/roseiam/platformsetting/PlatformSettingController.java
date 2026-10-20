package io.roseiam.platformsetting;

import io.roseiam.config.ApiException;
import io.roseiam.shared.UserPrincipal;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform-settings")
public class PlatformSettingController {
    private final PlatformSettingsService settings;

    public PlatformSettingController(PlatformSettingsService settings) {
        this.settings = settings;
    }

    @GetMapping
    public Map<String, Object> get() {
        return settings.getSettings();
    }

    @PatchMapping
    public Map<String, Object> patch(@RequestBody Map<String, Object> body, Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal actor)) throw ApiException.unauthorized();
        return settings.update(body, actor);
    }
}
