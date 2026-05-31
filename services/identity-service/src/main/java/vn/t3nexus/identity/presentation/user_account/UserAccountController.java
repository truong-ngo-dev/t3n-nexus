package vn.t3nexus.identity.presentation.user_account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.t3nexus.identity.application.user_account.resend_verification.ResendVerification;
import vn.t3nexus.identity.application.user_account.verify_email.VerifyEmail;
import vn.t3nexus.lib.web.commons.response.ApiResponse;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserAccountController {

    private final VerifyEmail verifyEmail;
    private final ResendVerification resendVerification;

    @GetMapping("/verify")
    public ApiResponse<VerifyEmail.Reply> verify(@RequestParam @NotBlank String token) {
        return ApiResponse.ok(verifyEmail.handle(new VerifyEmail.Command(token)));
    }

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendVerification(@RequestBody @Valid ResendVerificationRequest request) {
        resendVerification.handle(new ResendVerification.Command(request.email()));
    }

    public record ResendVerificationRequest(@NotBlank @Email String email) {}
}
