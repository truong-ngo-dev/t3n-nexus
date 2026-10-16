package vn.t3nexus.lib.observability.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Lưới an toàn cuối cho cookie: tiêu đề Cookie/Set-Cookie và cookie phiên, mã thiết bị, CSRF không lọt ra log. */
class SecretPatternsTest {

    @Test
    void a_cookie_header_line_is_masked_entirely() {
        String scrubbed = SecretPatterns.scrub("request failed Cookie: OAUTH2_SESSION=abc123; DEVICE_KEY=xyz789; theme=dark");

        assertThat(scrubbed).isEqualTo("request failed Cookie: ***");
    }

    @Test
    void a_set_cookie_header_is_masked_including_its_flags() {
        String scrubbed = SecretPatterns.scrub("Set-Cookie: DEVICE_KEY=abcdefgh; Max-Age=31536000; Path=/; HttpOnly");

        assertThat(scrubbed).isEqualTo("Set-Cookie: ***");
        assertThat(scrubbed).doesNotContain("abcdefgh");
    }

    @Test
    void known_cookies_are_masked_when_they_appear_alone_but_keep_their_names() {
        String scrubbed = SecretPatterns.scrub("got OAUTH2_SESSION=s3cr3t-session and DEVICE_KEY=k3y, XSRF-TOKEN=t0k3n");

        assertThat(scrubbed).isEqualTo("got OAUTH2_SESSION=*** and DEVICE_KEY=***, XSRF-TOKEN=***");
    }

    @Test
    void ordinary_text_with_similar_words_is_left_alone() {
        String text = "session expired for cookie policy page; sessionId counter=3";

        assertThat(SecretPatterns.scrub(text)).isEqualTo(text);
    }

    @Test
    void existing_patterns_still_work() {
        assertThat(SecretPatterns.scrub("Authorization: Bearer abcdefghijkl")).doesNotContain("abcdefghijkl");
    }
}
