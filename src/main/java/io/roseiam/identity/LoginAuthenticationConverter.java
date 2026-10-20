package io.roseiam.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Validator;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationConverter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

public class LoginAuthenticationConverter implements AuthenticationConverter {
    static final String EMAIL_ATTRIBUTE = LoginAuthenticationConverter.class.getName() + ".email";

    private final JsonMapper jsonMapper;
    private final Validator validator;

    public LoginAuthenticationConverter(JsonMapper jsonMapper, Validator validator) {
        this.jsonMapper = jsonMapper;
        this.validator = validator;
    }

    @Override
    public Authentication convert(HttpServletRequest request) {
        MediaType contentType;
        try {
            contentType = MediaType.parseMediaType(
                    request.getContentType() == null
                            ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                            : request.getContentType());
        } catch (InvalidMediaTypeException exception) {
            throw new LoginRequestAuthenticationException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content-Type must be application/json", List.of());
        }
        if (!MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
            throw new LoginRequestAuthenticationException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content-Type must be application/json", List.of());
        }
        AuthDtos.LoginRequest login;
        try {
            login = jsonMapper.readValue(request.getInputStream(), AuthDtos.LoginRequest.class);
        } catch (JacksonException | IOException exception) {
            throw new LoginRequestAuthenticationException("Login request body is not valid JSON", List.of());
        }
        if (login == null) {
            throw new LoginRequestAuthenticationException("Login request body is required", List.of());
        }
        List<Map<String, String>> violations = validator.validate(login).stream()
                .map(error -> Map.of(
                        "field", error.getPropertyPath().toString(),
                        "message", error.getMessage()))
                .sorted(Comparator.comparing(error -> error.get("field")))
                .toList();
        if (!violations.isEmpty()) {
            throw new LoginRequestAuthenticationException("Request validation failed", violations);
        }
        String email = login.email().trim().toLowerCase(Locale.ROOT);
        request.setAttribute(EMAIL_ATTRIBUTE, email);
        return UsernamePasswordAuthenticationToken.unauthenticated(email, login.password());
    }
}
