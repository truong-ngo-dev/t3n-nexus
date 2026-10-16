package vn.t3nexus.oauth2.presentation.login;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import vn.t3nexus.oauth2.infrastructure.security.extension.authentication.LoginRateLimitFilter;
import vn.t3nexus.oauth2.infrastructure.security.session.DeviceKeyCookieIssuer;

@Controller
public class LoginPageController {

    private final DeviceKeyCookieIssuer deviceKeyCookieIssuer;

    public LoginPageController(DeviceKeyCookieIssuer deviceKeyCookieIssuer) {
        this.deviceKeyCookieIssuer = deviceKeyCookieIssuer;
    }

    // "error", "locked", "unavailable" đọc thẳng qua "${param.xxx}" trong login.html (th:if đơn giản). Không nhận email qua địa chỉ.
    @GetMapping("/login")
    public String showLogin(HttpServletRequest request, HttpServletResponse response) {
        // KT-27: cấp mã thiết bị trước khi nhập mật khẩu để cả lần đăng nhập thất bại cũng mang mã
        deviceKeyCookieIssuer.issueIfMissing(request, response);
        return "login";
    }

    /**
     * Trang đăng nhập khi vượt giới hạn số lần thử (KT-15): bộ lọc giới hạn chuyển tiếp {@code POST /login} tới đây nên trả HTTP 429 kèm
     * {@code Retry-After}. Chỉ phục vụ khi bộ lọc đã đặt thời gian chờ; vào thẳng địa chỉ này thì về trang đăng nhập.
     */
    @RequestMapping(LoginRateLimitFilter.THROTTLED_PATH)
    public String throttled(HttpServletRequest request, HttpServletResponse response, Model model) {
        if (!(request.getAttribute(LoginRateLimitFilter.RETRY_AFTER_SECONDS) instanceof Long seconds)) {
            return "redirect:/login";
        }
        deviceKeyCookieIssuer.issueIfMissing(request, response);
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(seconds));
        model.addAttribute("throttled", true);
        model.addAttribute("retryMinutes", Math.max(1, (seconds + 59) / 60));
        return "login";
    }
}
