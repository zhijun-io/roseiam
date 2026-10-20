package io.roseiam.platformsetting;

public record PasswordResetRequest(String recipient, String token, String requestId) {}
