package vn.t3nexus.oauth2.infrastructure.adapter.service;

import org.junit.jupiter.api.Test;
import vn.t3nexus.oauth2.domain.login_session.DeviceKeyHash;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** KT-27 (phần bộ chuyển đổi): mã thiết bị 256 bit ngẫu nhiên, chỉ lưu bản băm SHA-256. */
class DeviceKeyAdaptersTest {

    private final Sha256DeviceKeyHasher hasher = new Sha256DeviceKeyHasher();
    private final SecureRandomDeviceKeyGenerator generator = new SecureRandomDeviceKeyGenerator();

    @Test
    void the_hash_is_the_known_sha256_hex_of_the_input() {
        // SHA-256("abc") là vectơ kiểm thử chuẩn
        assertThat(hasher.hash("abc")).isEqualTo(
                new DeviceKeyHash("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"));
    }

    @Test
    void the_hash_is_deterministic_64_hex_characters_and_not_the_input() {
        String raw = generator.generate();

        DeviceKeyHash first = hasher.hash(raw);

        assertThat(first).isEqualTo(hasher.hash(raw));
        assertThat(first.value()).hasSize(64).matches("[0-9a-f]{64}").isNotEqualTo(raw);
    }

    @Test
    void different_inputs_give_different_hashes() {
        assertThat(hasher.hash("a")).isNotEqualTo(hasher.hash("b"));
    }

    @Test
    void a_blank_input_is_rejected() {
        assertThatThrownBy(() -> hasher.hash(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> hasher.hash(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generated_keys_carry_256_bits_in_43_url_safe_characters_and_never_repeat() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String key = generator.generate();
            assertThat(key).hasSize(43).matches("[A-Za-z0-9_-]{43}");
            assertThat(seen.add(key)).isTrue();
        }
    }
}
