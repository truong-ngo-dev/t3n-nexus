package vn.t3nexus.oauth2.presentation.me;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.t3nexus.lib.web.commons.response.ApiResponse;
import vn.t3nexus.oauth2.application.session.revoke_session.RevokeSession;
import vn.t3nexus.oauth2.application.user_credential.change_password.ChangePassword;
import vn.t3nexus.oauth2.application.user_credential.get_password_status.GetPasswordStatus;
import vn.t3nexus.oauth2.application.user_credential.request_password_setup.RequestPasswordSetup;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeController {

    private final ChangePassword       changePassword;
    private final RequestPasswordSetup requestPasswordSetup;
    private final GetPasswordStatus    getPasswordStatus;
    private final RevokeSession        revokeSession;

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(
            Authentication authentication,
            @RequestBody @Valid ChangePasswordRequest request
    ) {
        changePassword.handle(new ChangePassword.Command(
                authentication.getName(),
                request.currentPassword(),
                request.newPassword()
        ));
        return ApiResponse.ok(null);
    }

    @PostMapping("/password/setup-request")
    public ApiResponse<Void> requestPasswordSetup(Authentication authentication) {
        requestPasswordSetup.handle(new RequestPasswordSetup.Command(authentication.getName()));
        return ApiResponse.ok(null);
    }

    @GetMapping("/password/status")
    public ApiResponse<GetPasswordStatus.Result> getPasswordStatus(Authentication authentication) {
        return ApiResponse.ok(getPasswordStatus.handle(new GetPasswordStatus.Query(authentication.getName())));
    }

    @DeleteMapping("/sessions/{ossId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeSession(Authentication authentication, @PathVariable String ossId) {
        Jwt jwt = ((JwtAuthenticationToken) authentication).getToken();
        revokeSession.handle(new RevokeSession.Command(
                authentication.getName(), ossId, jwt.getId()
        ));
    }

    public record ChangePasswordRequest(
            @NotBlank
            String currentPassword,

            @NotBlank
            @Size(min = 8, message = "New password must be at least 8 characters")
            String newPassword
    ) {}

}
