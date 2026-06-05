package vn.t3nexus.oauth2.infrastructure.security.mfa;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.ott.GenerateOneTimeTokenRequest;
import org.springframework.security.authentication.ott.OneTimeToken;
import org.springframework.security.authentication.ott.OneTimeTokenService;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.ott.OneTimeTokenGenerationSuccessHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import vn.t3nexus.oauth2.infrastructure.security.service.UserCredentialDetails;

import java.io.IOException;

@Controller
@RequestMapping("/mfa")
@RequiredArgsConstructor
public class MfaBridgeController {

    private final OneTimeTokenService                   oneTimeTokenService;
    private final OneTimeTokenGenerationSuccessHandler  generationSuccessHandler;

    /**
     * Auto-initiates OTT generation using the already-authenticated principal.
     * Bypasses Spring's default /ott/generate form (which asks for username again).
     */
    @GetMapping
    public void initiate(HttpServletRequest request, HttpServletResponse response,
            HttpSession session, Authentication authentication) throws IOException, ServletException {
        String email = (String) session.getAttribute("auth_email");
        if (email == null && authentication.getPrincipal() instanceof UserCredentialDetails details) {
            email = details.getEmail();
        }

        OneTimeToken token = oneTimeTokenService.generate(new GenerateOneTimeTokenRequest(email));
        generationSuccessHandler.handle(request, response, token);
    }

    @GetMapping("/verify")
    public String showForm(HttpSession session, Model model) {
        String email = (String) session.getAttribute("auth_email");
        model.addAttribute("maskedEmail", maskEmail(email));
        return "mfa/otp-form";
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "email đã đăng ký";
        String[] parts  = email.split("@", 2);
        String   local  = parts[0];
        String   masked = local.length() > 2
                ? local.charAt(0) + "*".repeat(local.length() - 2) + local.charAt(local.length() - 1)
                : "**";
        return masked + "@" + parts[1];
    }
}
