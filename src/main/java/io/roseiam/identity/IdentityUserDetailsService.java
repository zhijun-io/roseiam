package io.roseiam.identity;

import io.roseiam.user.UserService;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class IdentityUserDetailsService implements UserDetailsService {
    private final UserService users;
    private final String dummyHash;

    public IdentityUserDetailsService(UserService users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String email = username.trim().toLowerCase(Locale.ROOT);
        UserService.UserAccount user;
        try {
            user = users.findByEmail(email);
        } catch (DataAccessException exception) {
            throw new AuthenticationServiceException("Identity store is unavailable", exception);
        }
        if (user == null) throw new UsernameNotFoundException("User not found");
        return User.withUsername(user.email())
                .password(user.passwordHash() == null ? dummyHash : user.passwordHash())
                .disabled(user.passwordHash() == null
                        || user.emailVerifiedAt() == null
                        || !"ACTIVE".equals(user.status()))
                .authorities(AuthorityUtils.NO_AUTHORITIES)
                .build();
    }
}
