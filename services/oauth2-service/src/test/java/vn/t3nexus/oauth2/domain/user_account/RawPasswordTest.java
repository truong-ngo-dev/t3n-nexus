package vn.t3nexus.oauth2.domain.user_account;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** KT-07, KT-08 */
class RawPasswordTest {

    @Test
    void accepts_exactly_8_and_exactly_64_characters() {
        assertThat(RawPassword.of("abcdefg1").value()).hasSize(8);
        assertThat(RawPassword.of("a1" + "x".repeat(62)).value()).hasSize(64);
    }

    @Test
    void rejects_shorter_than_8_and_longer_than_64() {
        assertInvalid("abcdef1");
        assertInvalid("a1" + "x".repeat(63));
    }

    @Test
    void requires_a_letter_and_a_digit() {
        assertInvalid("12345678");
        assertInvalid("abcdefgh");
        assertInvalid("@$@$@$@$");
        assertInvalid("1234@$78");
        assertInvalid("abcd@$ef");
    }

    @Test
    void accepts_at_sign_and_dollar_sign() {
        assertThat(RawPassword.of("Mat@khau$123").value()).isEqualTo("Mat@khau$123");
    }

    @Test
    void rejects_whitespace_anywhere() {
        assertInvalid("abc 12345");
        assertInvalid(" abc12345");
        assertInvalid("abc12345 ");
        assertInvalid("abc\t12345");
        assertInvalid("abc12345\n");
    }

    @Test
    void rejects_emoji_and_accented_letters() {
        assertInvalid("abc12345😀");
        assertInvalid("matkhẩu123");
        assertInvalid("mậtkhẩu1234");
    }

    @Test
    void rejects_special_characters_other_than_at_and_dollar() {
        for (char c : "!#%^&*()_-+=.,;:'\"/\\|?<>[]{}~`".toCharArray()) {
            assertInvalid("abc123" + c + "x");
        }
    }

    @Test
    void null_is_invalid() {
        assertInvalid(null);
    }

    @Test
    void never_prints_its_value() {
        assertThat(RawPassword.of("secret$pass1").toString()).doesNotContain("secret");
    }

    private static void assertInvalid(String raw) {
        assertThatThrownBy(() -> RawPassword.of(raw))
                .as("password %s", raw)
                .isInstanceOf(UserAccountException.class)
                .extracting(e -> ((UserAccountException) e).getErrorCode())
                .isEqualTo(UserAccountErrorCode.PASSWORD_INVALID);
    }
}
