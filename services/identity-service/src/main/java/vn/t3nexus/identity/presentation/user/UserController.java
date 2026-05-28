package vn.t3nexus.identity.presentation.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.t3nexus.identity.application.user.customer_register.CustomerRegister;
import vn.t3nexus.identity.application.user.resend_verification.ResendVerification;
import vn.t3nexus.identity.application.user.verify_email.VerifyEmail;
import vn.t3nexus.lib.web.commons.response.ApiResponse;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final CustomerRegister customerRegister;
    private final VerifyEmail verifyEmail;
    private final ResendVerification resendVerification;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CustomerRegister.Reply> register(@RequestBody @Valid RegisterRequest request) {
        return ApiResponse.ok(customerRegister.handle(new CustomerRegister.Command(request.email(), request.password(), request.fullName())));
    }

    @GetMapping("/verify")
    public ApiResponse<VerifyEmail.Reply> verify(@RequestParam @NotBlank String token) {
        return ApiResponse.ok(verifyEmail.handle(new VerifyEmail.Command(token)));
    }

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendVerification(@RequestBody @Valid ResendVerificationRequest request) {
        resendVerification.handle(new ResendVerification.Command(request.email()));
    }

    public record RegisterRequest(@NotBlank @Email String email,
                                  @NotBlank String password,
                                  @NotBlank String fullName) {}

    public record ResendVerificationRequest(@NotBlank @Email String email) {}
}
