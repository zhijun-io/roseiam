package io.roseiam.identity;

import java.util.Set;

public record UserPrincipal(Long userId, String email, Set<String> permissionCodes, Set<String> roleCodes) {}
