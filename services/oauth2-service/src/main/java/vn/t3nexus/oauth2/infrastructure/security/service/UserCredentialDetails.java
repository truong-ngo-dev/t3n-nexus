package vn.t3nexus.oauth2.infrastructure.security.service;

import org.springframework.security.core.GrantedAuthority;

import java.io.Serial;
import java.util.Collection;

/**
 * Custom UserDetails để carry thêm claims vào JWT token generation.
 * getUsername() returns userId — used as JWT sub claim.
 * TODO [business]: thêm field contexts (List<ContextDto>) khi implement multi-context claim.
 */
public class UserCredentialDetails extends org.springframework.security.core.userdetails.User {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String  userId;
    private final String  email;
    private final boolean mfaEnabled;

    public UserCredentialDetails(String userId, String email, String password, boolean enabled,
                                 boolean accountNonExpired, boolean credentialsNonExpired,
                                 boolean accountNonLocked,
                                 Collection<? extends GrantedAuthority> authorities,
                                 boolean mfaEnabled) {
        super(userId, password, enabled, accountNonExpired, credentialsNonExpired, accountNonLocked, authorities);
        this.userId     = userId;
        this.email      = email;
        this.mfaEnabled = mfaEnabled;
    }

    public String  getUserId()    { return userId; }
    public String  getEmail()     { return email; }
    public boolean isMfaEnabled() { return mfaEnabled; }
}
