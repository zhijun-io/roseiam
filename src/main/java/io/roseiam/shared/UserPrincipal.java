package io.roseiam.shared;

import java.util.Set;

public record UserPrincipal(Long userId, String email, Set<String> permissionCodes, Set<String> roleCodes) {}
