package vn.t3nexus.oauth2.infrastructure.security.session;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** KT-27: cookie mã thiết bị được cấp trước khi nhập mật khẩu, đúng cờ bảo mật, không đổi giá trị khi đã có. */
class DeviceKeyCookieIssuerTest {

    private final DeviceKeyCookieIssuer issuer = new DeviceKeyCookieIssuer(() -> "generated-key",
            new DeviceKeyCookieProperties(Duration.ofDays(365), true));

    @Test
    void a_browser_without_the_cookie_gets_a_new_one_with_the_security_flags() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        String value = issuer.issueIfMissing(new MockHttpServletRequest(), response);

        assertThat(value).isEqualTo("generated-key");
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).singleElement().satisfies(header ->
                assertThat(header).startsWith("DEVICE_KEY=generated-key").contains("HttpOnly", "Secure", "SameSite=Lax",
                        "Path=/", "Max-Age=31536000"));
    }

    @Test
    void a_browser_that_already_has_the_cookie_keeps_its_value_and_gets_no_new_header() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(DeviceKeyCookie.NAME, "existing-key"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(issuer.issueIfMissing(request, response)).isEqualTo("existing-key");
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    @Test
    void renewing_extends_the_same_value_and_does_nothing_without_a_cookie() {
        MockHttpServletRequest with = new MockHttpServletRequest();
        with.setCookies(new Cookie(DeviceKeyCookie.NAME, "existing-key"));
        MockHttpServletResponse renewed = new MockHttpServletResponse();
        MockHttpServletResponse untouched = new MockHttpServletResponse();

        issuer.renew(with, renewed);
        issuer.renew(new MockHttpServletRequest(), untouched);

        assertThat(renewed.getHeaders(HttpHeaders.SET_COOKIE)).singleElement().satisfies(header ->
                assertThat(header).startsWith("DEVICE_KEY=existing-key").contains("Max-Age=31536000"));
        assertThat(untouched.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    @Test
    void secure_flag_follows_the_configuration() {
        DeviceKeyCookieIssuer http = new DeviceKeyCookieIssuer(() -> "k",
                new DeviceKeyCookieProperties(Duration.ofDays(1), false));
        MockHttpServletResponse response = new MockHttpServletResponse();

        http.issueIfMissing(new MockHttpServletRequest(), response);

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).doesNotContain("Secure");
    }
}
