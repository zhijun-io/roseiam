package io.roseiam.platformsetting;

public record InvitationMailRequested(String recipient, String token, String requestId) {}
