package vn.t3nexus.oauth2.presentation.user_account;

import jakarta.validation.Valid;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.t3nexus.lib.observability.http.LogRequestFields;
import vn.t3nexus.lib.web.commons.response.ApiResponse;
import vn.t3nexus.oauth2.application.user_account.register_user.RegisterUser;

@RestController
@RequiredArgsConstructor
public class UserAccountController {

    private final RegisterUser registerUser;

    // Path trần, không tiền tố /api — khớp quy ước của /login, /mfa, /password/setup (cùng service)
    // và api-gateway route /auth/** (stripPrefix 1 segment): public /auth/register → /register nội bộ.
    // Nằm ngoài /api/** nên không bị apiResourceServerFilterChain (JWT) chặn — xem defaultSecurityFilterChain.
    // Khi lỗi, chỉ email được ghi vào log (mật khẩu không bao giờ).
    @LogRequestFields("email")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RegisterUser.Result> register(@RequestBody @Valid RegisterRequest request) {
        RegisterUser.Command command = new RegisterUser.Command(
                request.email(),
                request.password()
        );
        return ApiResponse.ok(registerUser.handle(command));
    }

    // Vai không phải đầu vào (INV-ATH-06): trường lạ gửi kèm bị Jackson bỏ qua. Luật email và mật khẩu nằm ở miền,
    // nên ở đây chỉ chặn thiếu trường.
    public record RegisterRequest(
            @NotNull String email,
            @NotNull String password
    ) {}
}
