package io.roseiam.platformsetting;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PlatformSettingInitializer implements ApplicationRunner {

    private final PlatformSettingService settings;

    public PlatformSettingInitializer(PlatformSettingService settings) {
        this.settings = settings;
    }

    @Override
    public void run(ApplicationArguments args) {
        settings.ensureDefaults();
    }
}
