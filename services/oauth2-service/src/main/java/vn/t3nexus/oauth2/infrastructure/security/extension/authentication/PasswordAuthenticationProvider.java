package vn.t3nexus.oauth2.infrastructure.security.extension.authentication;

import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import vn.t3nexus.oauth2.application.login_session.authenticate_with_password.AuthenticateWithPassword;
import vn.t3nexus.oauth2.domain.login_session.DeviceKeyHash;
import vn.t3nexus.oauth2.domain.user_account.UserAccountErrorCode;
import vn.t3nexus.oauth2.domain.user_account.UserAccountException;

import java.time.Clock;
import java.util.List;

/**
 * Bộ xác thực mật khẩu của form đăng nhập: chạy use case {@link AuthenticateWithPassword} (chuẩn hóa email, gộp lỗi theo
 * INV-ATH-03, ghi {@code LoginFailedEvent}). Khai báo là một {@code AuthenticationProvider} nên Spring không tự dựng thêm
 * {@code DaoAuthenticationProvider}: nếu có, mỗi lần sai mật khẩu sẽ bị băm lần hai. Phiên chưa mở ở đây mà ở bộ xử lý thành công
 * (ADR-0003 mục 6).
 *
 * <p>Không đặt {@code details} cho token trả về: {@code ProviderManager} chép {@code details} của yêu cầu ({@link LoginRequestDetails},
 * chỉ mang bản băm mã thiết bị, địa chỉ mạng, trình duyệt) sang token như hành vi mặc định của Spring, và token được lưu vào session
 * và bản ghi ủy quyền. Chủ thể là {@link UserAccountDetails} để khớp bộ đọc JSON của bản ghi ủy quyền; không mang hàm băm mật khẩu.
 * MFA nằm ngoài C0 nên {@code mfaEnabled} luôn tắt ở đường này.
 */
@Component
public class PasswordAuthenticationProvider implements AuthenticationProvider {

    private final AuthenticateWithPassword authenticateWithPassword;
    private final Clock                    clock;

    public PasswordAuthenticationProvider(AuthenticateWithPassword authenticateWithPassword, Clock clock) {
        this.authenticateWithPassword = authenticateWithPassword;
        this.clock                    = clock;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        LoginRequestDetails details = authentication.getDetails() instanceof LoginRequestDetails d ? d : null;
        String password = authentication.getCredentials() == null ? "" : authentication.getCredentials().toString();
        try {
            AuthenticateWithPassword.Result result = authenticateWithPassword.handle(new AuthenticateWithPassword.Command(
                    authentication.getName(), password,
                    details == null || details.getDeviceKeyHash() == null ? null : new DeviceKeyHash(details.getDeviceKeyHash()),
                    details == null ? null : details.getIpAddress(),
                    details == null ? null : details.getUserAgent()));
            List<SimpleGrantedAuthority> roleAuthorities = List.of(new SimpleGrantedAuthority("ROLE_" + result.role().name()));
            // Spring Security 7: auth_time của ID token lấy từ FactorGrantedAuthority (yếu tố mật khẩu và thời điểm xác thực);
            // DaoAuthenticationProvider tự thêm, bộ xác thực của ta phải tự thêm.
            List<GrantedAuthority> authorities = List.of(roleAuthorities.getFirst(),
                    FactorGrantedAuthority.withAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY).issuedAt(clock.instant()).build());
            UserAccountDetails principal = new UserAccountDetails(result.userAccountId().getValueAsString(), "", "",
                    true, true, true, true, roleAuthorities, false);
            return UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
        } catch (UserAccountException e) {
            if (e.getErrorCode() == UserAccountErrorCode.INVALID_CREDENTIALS) {
                throw new BadCredentialsException("Bad credentials");
            }
            if (e.getErrorCode() == UserAccountErrorCode.ACCOUNT_NOT_ACTIVE) {
                throw new LockedException("Account is not active");
            }
            throw new InternalAuthenticationServiceException("Cannot authenticate", e);
        }
    }

    @Override
    public boolean supports(@NonNull Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
