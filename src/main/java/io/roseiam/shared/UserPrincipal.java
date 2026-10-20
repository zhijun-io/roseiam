package io.roseiam.shared;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;

public record UserPrincipal(Long userId, String email, Set<String> permissionCodes, Set<String> roleCodes)
        implements OAuth2AuthenticatedPrincipal {

    @Override
    public Map<String, Object> getAttributes() {
        return Map.of("sub", userId.toString(), "email", email);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return permissionCodes.stream().map(SimpleGrantedAuthority::new).toList();
    }

    @Override
    public String getName() {
        return userId.toString();
    }
}
