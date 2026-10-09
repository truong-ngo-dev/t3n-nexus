package vn.t3nexus.oauth2.infrastructure.adapter.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import vn.t3nexus.oauth2.domain.user_account.PasswordHash;
import vn.t3nexus.oauth2.domain.user_account.PasswordHasher;
import vn.t3nexus.oauth2.domain.user_account.RawPassword;

@Service
@RequiredArgsConstructor
public class PasswordHasherAdapter implements PasswordHasher {

    private final PasswordEncoder passwordEncoder;

    @Override
    public PasswordHash hash(RawPassword rawPassword) {
        return PasswordHash.ofHashed(passwordEncoder.encode(rawPassword.value()));
    }

    @Override
    public boolean verify(String attempt, PasswordHash hash) {
        return passwordEncoder.matches(attempt, hash.getHashedValue());
    }
}
