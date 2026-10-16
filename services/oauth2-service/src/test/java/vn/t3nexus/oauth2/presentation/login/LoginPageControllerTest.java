package vn.t3nexus.oauth2.presentation.login;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ConcurrentModel;
import vn.t3nexus.oauth2.infrastructure.security.extension.authentication.LoginRateLimitFilter;
import vn.t3nexus.oauth2.infrastructure.security.session.DeviceKeyCookieIssuer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Trang đăng nhập; trang 429 khi vượt giới hạn số lần thử (KT-15). */
class LoginPageControllerTest {

    private final DeviceKeyCookieIssuer issuer = mock(DeviceKeyCookieIssuer.class);
    private final LoginPageController controller = new LoginPageController(issuer);

    @Test
    void the_throttled_page_answers_429_with_retry_after_and_the_wait_in_minutes() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", LoginRateLimitFilter.THROTTLED_PATH);
        request.setAttribute(LoginRateLimitFilter.RETRY_AFTER_SECONDS, 300L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.throttled(request, response, model);

        assertThat(view).isEqualTo("login");
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("300");
        assertThat(model.getAttribute("throttled")).isEqualTo(true);
        assertThat(model.getAttribute("retryMinutes")).isEqualTo(5L);
        verify(issuer).issueIfMissing(request, response);
    }

    @Test
    void the_wait_is_rounded_up_to_whole_minutes_and_is_never_zero() {
        for (long[] c : new long[][]{{1, 1}, {60, 1}, {61, 2}, {900, 15}}) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", LoginRateLimitFilter.THROTTLED_PATH);
            request.setAttribute(LoginRateLimitFilter.RETRY_AFTER_SECONDS, c[0]);
            ConcurrentModel model = new ConcurrentModel();

            controller.throttled(request, new MockHttpServletResponse(), model);

            assertThat(model.getAttribute("retryMinutes")).as("seconds " + c[0]).isEqualTo(c[1]);
        }
    }

    @Test
    void opening_the_throttled_address_directly_goes_back_to_the_login_page() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.throttled(new MockHttpServletRequest("GET", LoginRateLimitFilter.THROTTLED_PATH), response, new ConcurrentModel());

        assertThat(view).isEqualTo("redirect:/login");
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
