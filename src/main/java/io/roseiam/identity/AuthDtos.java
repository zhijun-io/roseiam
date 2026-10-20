package io.roseiam.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class AuthDtos {

    private AuthDtos() {}

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank String password) {}

    public record LoginResponse(
            String accessToken, String tokenType, Instant idleExpiresAt, Instant absoluteExpiresAt) {}

    public record PasswordRecoveryRequest(
            @NotBlank @Email @Size(max = 254) String email) {}

    public record PasswordRecoveryCompletion(
            @NotBlank String token, @NotBlank String password) {}

    public record UserCreateRequest(
            @NotBlank @Email @Size(max = 254) String email, Set<Long> roleIds) {}

    public record UserCreateResponse(Long id, Instant expiresAt) {}

    public record UserAcceptInvitationRequest(
            @NotBlank String token,
            @NotBlank String password,
            @NotBlank @Size(max = 100) String name) {}

    public record UserRequest(@NotBlank @Size(max = 100) String name) {}

    public record UserResponse(
            Long id,
            String email,
            Instant emailVerifiedAt,
            String name,
            String status,
            List<String> roles,
            Instant createdAt,
            Instant updatedAt) {}

    public record UserPageResponse(List<UserResponse> items, int page, int size, long totalElements, int totalPages) {}

    public record UserMeResponse(Long id, String email, Instant emailVerifiedAt, String name, String status) {}

    public record InvitationDelivery(String email, String token) {}

    public record CreatedInvitation(UserCreateResponse response, InvitationDelivery delivery) {}

    public record RecoveryDelivery(String email, String token) {}
}
