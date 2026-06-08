package vn.t3nexus.oauth2.presentation.user_credential;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.t3nexus.lib.web.commons.response.ApiResponse;
import vn.t3nexus.oauth2.application.user_credential.register_user.RegisterUser;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class UserCredentialController {

    private final RegisterUser registerUser;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RegisterUser.Result> register(@RequestBody @Valid RegisterRequest request) {
        RegisterUser.Command command = new RegisterUser.Command(
                request.email(),
                request.password(),
                request.role(),
                request.fullName()
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
