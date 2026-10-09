package vn.t3nexus.oauth2.domain.user_account;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** KT-04, KT-06 */
class EmailTest {

    @Test
    void normalizes_trim_and_lower_case() {
        assertThat(Email.of("  Nguyen.Van@Example.COM \t").value()).isEqualTo("nguyen.van@example.com");
    }

    @Test
    void lower_cases_without_depending_on_the_default_locale() {
        // "I" hoa trong locale Thổ Nhĩ Kỳ sẽ thành "ı" nếu dùng toLowerCase() mặc định
        java.util.Locale previous = java.util.Locale.getDefault();
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
        try {
            assertThat(Email.of("INFO@EXAMPLE.COM").value()).isEqualTo("info@example.com");
        } finally {
            java.util.Locale.setDefault(previous);
        }
    }

    @Test
    void same_email_in_different_case_is_equal() {
        assertThat(Email.of("A@b.com")).isEqualTo(Email.of(" a@B.COM "));
    }

    @Test
    void accepts_254_characters_and_rejects_255() {
        String domain = "@example.com";
        String at254 = "a".repeat(254 - domain.length()) + domain;
        String at255 = "a".repeat(255 - domain.length()) + domain;

        assertThat(Email.of(at254).value()).hasSize(254);
        assertThatThrownBy(() -> Email.of(at255)).isInstanceOf(UserAccountException.class)
                .extracting(e -> ((UserAccountException) e).getErrorCode()).isEqualTo(UserAccountErrorCode.EMAIL_INVALID);
    }

    @Test
    void rejects_malformed_emails() {
        for (String bad : new String[]{null, "", "   ", "no-at-sign", "a@", "@b.com", "a@b", "a@@b.com", "a b@c.com", "a@b..com", "a@.com"}) {
            assertThatThrownBy(() -> Email.of(bad))
                    .as("email %s", bad)
                    .isInstanceOf(UserAccountException.class)
                    .extracting(e -> ((UserAccountException) e).getErrorCode())
                    .isEqualTo(UserAccountErrorCode.EMAIL_INVALID);
        }
    }

    @Test
    void exposes_domain_part() {
        assertThat(Email.of("a@HR.t3nexus.com.vn").domain()).isEqualTo("hr.t3nexus.com.vn");
    }
}
