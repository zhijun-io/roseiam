package io.roseiam.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class AuthDtos {

    private AuthDtos() {}

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank String password) {}

    public record LoginResponse(
            String accessToken, String tokenType, Instant idleExpiresAt, Instant absoluteExpiresAt) {}

    public record PasswordResetRequest(
            @NotBlank @Email @Size(max = 254) String email) {}

    public record PasswordResetCompletion(
            @NotBlank String token, @NotBlank String password) {}
}
