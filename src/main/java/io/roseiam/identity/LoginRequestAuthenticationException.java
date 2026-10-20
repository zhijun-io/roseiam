package io.roseiam.identity;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;

public class LoginRequestAuthenticationException extends AuthenticationException {
    private final HttpStatus status;
    private final List<Map<String, String>> violations;

    public LoginRequestAuthenticationException(String message, List<Map<String, String>> violations) {
        this(HttpStatus.BAD_REQUEST, message, violations);
    }

    public LoginRequestAuthenticationException(
            HttpStatus status, String message, List<Map<String, String>> violations) {
        super(message);
        this.status = status;
        this.violations = List.copyOf(violations);
    }

    public HttpStatus status() {
        return status;
    }

    public List<Map<String, String>> violations() {
        return violations;
    }
}
