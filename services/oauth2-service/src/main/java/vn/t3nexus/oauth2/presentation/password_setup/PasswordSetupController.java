package vn.t3nexus.oauth2.presentation.password_setup;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import vn.t3nexus.oauth2.application.user_credential.setup_password.SetupPassword;

@Controller
@RequestMapping("/password/setup")
@RequiredArgsConstructor
public class PasswordSetupController {

    private final SetupPassword setupPassword;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @GetMapping
    public String showForm(@RequestParam(required = false) String token,
                           @RequestParam(required = false) String error,
                           Model model) {
        if (token == null || token.isBlank()) {
            return "redirect:/login?error";
        }
        model.addAttribute("token", token);
        model.addAttribute("error", error);
        return "password-setup";
    }

    @PostMapping
    public String submit(@RequestParam String token,
                         @RequestParam String password,
                         @RequestParam String confirmPassword) {
        if (!password.equals(confirmPassword)) {
            return "redirect:/password/setup?token=" + token + "&error=mismatch";
        }
        if (password.length() < 8) {
            return "redirect:/password/setup?token=" + token + "&error=too_short";
        }
        try {
            setupPassword.handle(new SetupPassword.Command(token, password));
            return "redirect:/password/setup/success";
        } catch (Exception e) {
            return "redirect:/password/setup?token=" + token + "&error=invalid";
        }
    }

    @GetMapping("/success")
    public String showSuccess(Model model) {
        model.addAttribute("frontendUrl", frontendUrl);
        return "password-setup-success";
    }
}
