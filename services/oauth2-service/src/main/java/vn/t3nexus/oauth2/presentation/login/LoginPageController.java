package vn.t3nexus.oauth2.presentation.login;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LoginPageController {

    // "error", "locked", "unverified" vẫn đọc thẳng qua "${param.xxx}" trong login.html
    // (th:if đơn giản — không đi qua "th:attr", không chạm restricted SpEL context).
    // Riêng "email" phải đi qua Model vì template dùng nó trong "th:attr" (data-email cho
    // nút resend) — Thymeleaf 3.1 chặn object instantiation khi th:attr đọc thẳng implicit
    // object "param" (phòng SpEL injection từ query string).
    @GetMapping("/login")
    public String showLogin(@RequestParam(required = false) String email, Model model) {
        model.addAttribute("email", email);
        return "login";
    }
}
