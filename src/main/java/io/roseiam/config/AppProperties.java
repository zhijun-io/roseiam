package io.roseiam.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app")
public class AppProperties {
    private String frontendBaseUrl = "http://localhost:3000";
    private String smtpEncryptionKey = "";
    private String bootstrapAdminEmail = "";
    private List<String> corsAllowedOrigins = new ArrayList<>();

    @Valid
    private final Security security = new Security();

    public String getFrontendBaseUrl() {
        return frontendBaseUrl;
    }

    public void setFrontendBaseUrl(String frontendBaseUrl) {
        this.frontendBaseUrl = frontendBaseUrl;
    }

    public String getSmtpEncryptionKey() {
        return smtpEncryptionKey;
    }

    public void setSmtpEncryptionKey(String smtpEncryptionKey) {
        this.smtpEncryptionKey = smtpEncryptionKey;
    }

    public String getBootstrapAdminEmail() {
        return bootstrapAdminEmail;
    }

    public void setBootstrapAdminEmail(String bootstrapAdminEmail) {
        this.bootstrapAdminEmail = bootstrapAdminEmail;
    }

    public List<String> getCorsAllowedOrigins() {
        return corsAllowedOrigins;
    }

    public void setCorsAllowedOrigins(List<String> corsAllowedOrigins) {
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    public Security getSecurity() {
        return security;
    }

    public static class Security {
        private Duration idleSessionTimeout = Duration.ofMinutes(15);
        private Duration absoluteSessionTimeout = Duration.ofHours(8);

        public Duration getIdleSessionTimeout() {
            return idleSessionTimeout;
        }

        public void setIdleSessionTimeout(Duration idleSessionTimeout) {
            this.idleSessionTimeout = idleSessionTimeout;
        }

        public Duration getAbsoluteSessionTimeout() {
            return absoluteSessionTimeout;
        }

        public void setAbsoluteSessionTimeout(Duration absoluteSessionTimeout) {
            this.absoluteSessionTimeout = absoluteSessionTimeout;
        }

        @AssertTrue(message = "Session timeouts must be positive and idle timeout must not exceed absolute timeout")
        public boolean isSessionTimeoutsValid() {
            return idleSessionTimeout != null
                    && absoluteSessionTimeout != null
                    && !idleSessionTimeout.isNegative()
                    && !idleSessionTimeout.isZero()
                    && !absoluteSessionTimeout.isNegative()
                    && !absoluteSessionTimeout.isZero()
                    && idleSessionTimeout.compareTo(absoluteSessionTimeout) <= 0;
        }
    }
}
