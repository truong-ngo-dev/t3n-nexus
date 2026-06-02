package vn.t3nexus.oauth2.infrastructure.security.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import vn.t3nexus.oauth2.domain.user_credential.UserCredential;
import vn.t3nexus.oauth2.domain.user_credential.UserCredentialRepository;

import java.util.List;

/**
 * UserDetailsService cho Spring Security form login.
 * Principal name = userId → sub claim trong JWT sẽ = userId.
 *
 * TODO [business]: adapt mapToUserDetails() theo model UserCredential của t3n-nexus.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2UserDetailsService implements UserDetailsService {

    private final UserCredentialRepository userCredentialRepository;

    @Override
    public @NotNull UserDetails loadUserByUsername(@NotNull String email) throws UsernameNotFoundException {
        if (!StringUtils.hasText(email)) {
            throw new UsernameNotFoundException("Email is missing");
        }

        try {
            // TODO [business]: UserCredentialRepository chưa có findByEmail — thêm method vào interface
            UserCredential credential = userCredentialRepository.findByEmail(email)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

            return mapToUserDetails(credential);
        } catch (UsernameNotFoundException e) {
            log.warn("[loadUserByUsername] User not found: '{}'", email);
            throw e;
        } catch (Exception e) {
            log.error("[loadUserByUsername] Cannot retrieve user '{}': {}", email, e.getMessage());
            throw new UsernameNotFoundException("Cannot retrieve user: " + email, e);
        }
    }

    private static UserDetails mapToUserDetails(UserCredential credential) {
        // TODO [business]: adapt theo trạng thái isActive/isLocked của UserCredential
        return new UserCredentialDetails(
                credential.getId().getValueAsString(),                              // userId → principal name → JWT sub
                credential.getEmail(),
                credential.getPassword() != null ? credential.getPassword().getHashedValue() : "",
                credential.isActive(),                                              // enabled
                true,                                                               // accountNonExpired
                true,                                                               // credentialsNonExpired
                !credential.isLocked(),                                             // accountNonLocked
                List.of(new SimpleGrantedAuthority("ROLE_" + credential.getRole().name())),
                credential.isMfaEnabled()
        );
    }
}
