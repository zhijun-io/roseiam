package io.roseiam.platformsetting;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PlatformSettingsInitializer implements ApplicationRunner {

    private final PlatformSettingsService settings;

    public PlatformSettingsInitializer(PlatformSettingsService settings) {
        this.settings = settings;
    }

    @Override
    public void run(ApplicationArguments args) {
        settings.ensureDefaults();
    }
}
