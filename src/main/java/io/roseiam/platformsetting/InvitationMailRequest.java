package io.roseiam.platformsetting;

public record InvitationMailRequest(String recipient, String token, String requestId) {}
