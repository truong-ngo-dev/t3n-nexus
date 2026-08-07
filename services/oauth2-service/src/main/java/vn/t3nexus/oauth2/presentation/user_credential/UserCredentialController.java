package vn.t3nexus.oauth2.presentation.user_credential;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.t3nexus.lib.web.commons.response.ApiResponse;
import vn.t3nexus.oauth2.application.user_credential.register_user.RegisterUser;
import vn.t3nexus.oauth2.infrastructure.cross_cutting.utils.IpAddressExtractor;

@RestController
@RequiredArgsConstructor
public class UserCredentialController {

    private final RegisterUser registerUser;

    // Path trần, không tiền tố /api — khớp quy ước của /login, /mfa, /password/setup (cùng service)
    // và api-gateway route /auth/** (stripPrefix 1 segment): public /auth/register → /register nội bộ.
    // Nằm ngoài /api/** nên không bị apiResourceServerFilterChain (JWT) chặn — xem defaultSecurityFilterChain.
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RegisterUser.Result> register(@RequestBody @Valid RegisterRequest request,
                                                       HttpServletRequest httpRequest) {
        RegisterUser.Command command = new RegisterUser.Command(
                request.email(),
                request.password(),
                request.role(),
                request.fullName(),
                IpAddressExtractor.extract(httpRequest)
        );
        return ApiResponse.ok(registerUser.handle(command));
    }

    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank String password,
            @NotBlank String role,
            @NotBlank String fullName
    ) {}
}
