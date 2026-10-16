package vn.t3nexus.oauth2.infrastructure.adapter.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import vn.t3nexus.oauth2.domain.user_account.PasswordHash;
import vn.t3nexus.oauth2.domain.user_account.RawPassword;

import static org.assertj.core.api.Assertions.assertThat;

/** Bộ băm thật (ADR-0002): bcrypt hệ số 10, cùng bộ mã hóa mà SecurityConfiguration khai báo. */
class PasswordHasherAdapterTest {

    private final PasswordHasherAdapter hasher = new PasswordHasherAdapter(new BCryptPasswordEncoder(10));

    @Test
    void hashes_then_verifies_the_same_password() {
        PasswordHash hash = hasher.hash(RawPassword.of("matkhau123"));

        assertThat(hasher.verify("matkhau123", hash)).isTrue();
    }

    @Test
    void a_hash_made_with_a_lower_cost_needs_a_rehash_and_the_rehash_is_at_the_current_cost_and_still_verifies() {
        PasswordHash weak = PasswordHash.ofHashed(new BCryptPasswordEncoder(4).encode("matkhau123"));

        assertThat(hasher.needsRehash(weak)).isTrue();
        PasswordHash upgraded = hasher.rehash("matkhau123");
        assertThat(upgraded.getHashedValue()).startsWith("$2a$10$");
        assertThat(hasher.verify("matkhau123", upgraded)).isTrue();
        assertThat(hasher.needsRehash(upgraded)).isFalse();
    }

    @Test
    void rejects_a_wrong_password() {
        PasswordHash hash = hasher.hash(RawPassword.of("matkhau123"));

        assertThat(hasher.verify("matkhau124", hash)).isFalse();
        assertThat(hasher.verify("", hash)).isFalse();
    }

    @Test
    void stored_value_is_self_describing_bcrypt_cost_10_and_not_the_password() {
        PasswordHash hash = hasher.hash(RawPassword.of("matkhau123"));

        assertThat(hash.getHashedValue()).startsWith("$2a$10$").doesNotContain("matkhau123");
    }

    @Test
    void same_password_hashes_differently_each_time() {
        RawPassword password = RawPassword.of("matkhau123");

        assertThat(hasher.hash(password)).isNotEqualTo(hasher.hash(password));
    }

    @Test
    void longest_valid_password_is_not_truncated() {
        String base = "a1" + "x".repeat(61);
        PasswordHash hash = hasher.hash(RawPassword.of(base + "y")); // đúng 64 ký tự

        assertThat(hasher.verify(base + "y", hash)).isTrue();
        assertThat(hasher.verify(base + "z", hash)).isFalse(); // ký tự cuối khác thì không khớp
    }
}
