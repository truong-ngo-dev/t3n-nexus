package vn.t3nexus.oauth2.infrastructure.adapter.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import vn.t3nexus.oauth2.domain.user_credential.PasswordService;

@Service
@RequiredArgsConstructor
public class PasswordServiceAdapter implements PasswordService {

    private final PasswordEncoder passwordEncoder;

    @Override
    public String hash(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    @Override
    public boolean verify(String rawPassword, String hashedPassword) {
        return passwordEncoder.matches(rawPassword, hashedPassword);
    }
}
