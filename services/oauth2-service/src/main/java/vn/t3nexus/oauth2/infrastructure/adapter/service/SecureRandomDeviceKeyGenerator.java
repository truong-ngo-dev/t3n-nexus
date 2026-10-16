package vn.t3nexus.oauth2.infrastructure.adapter.service;

import org.springframework.stereotype.Component;
import vn.t3nexus.oauth2.domain.login_session.DeviceKeyGenerator;

import java.security.SecureRandom;
import java.util.Base64;

/** Sinh mã thiết bị: 256 bit ngẫu nhiên mật mã, mã hóa base64 url không đệm (43 ký tự) để đặt vào cookie (ADR-0005). */
@Component
public class SecureRandomDeviceKeyGenerator implements DeviceKeyGenerator {

    private static final int BYTES = 32;   // 256 bit

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate() {
        byte[] bytes = new byte[BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
