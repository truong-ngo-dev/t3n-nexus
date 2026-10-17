package vn.t3nexus.oauth2.application.login_session.authenticate_with_password;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.oauth2.domain.login_session.DeviceKeyHash;
import vn.t3nexus.oauth2.domain.login_session.LoginContext;
import vn.t3nexus.oauth2.domain.login_session.LoginFailedEvent;
import vn.t3nexus.oauth2.domain.user_account.Email;
import vn.t3nexus.oauth2.domain.user_account.PasswordHash;
import vn.t3nexus.oauth2.domain.user_account.PasswordHasher;
import vn.t3nexus.oauth2.domain.user_account.RawPassword;
import vn.t3nexus.oauth2.domain.user_account.Role;
import vn.t3nexus.oauth2.domain.user_account.UserAccount;
import vn.t3nexus.oauth2.domain.user_account.UserAccountException;
import vn.t3nexus.oauth2.domain.user_account.UserAccountId;
import vn.t3nexus.oauth2.domain.user_account.UserAccountRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Xác thực email và mật khẩu (feature 02, phần A, nửa đầu của đăng nhập): chuẩn hóa email, tìm tài khoản, kiểm mật khẩu,
 * kiểm trạng thái. Không mở phiên: phiên mở ở {@code OpenLoginSession}, ở điểm cuối chuỗi xác thực sau mọi lớp
 * (ADR-0003 mục 6), nên sau này xác thực nhiều lớp chèn vào giữa hai use case mà không sửa phiên. Tương ứng điểm móc của
 * Spring: use case này chạy trong {@code AuthenticationProvider}, use case mở phiên chạy trong bộ xử lý thành công.
 *
 * <p><b>INV-ATH-03:</b> email không tồn tại (kể cả email sai định dạng), tài khoản không có mật khẩu và mật khẩu sai cùng
 * trả {@code INVALID_CREDENTIALS}, và cùng tốn đúng một lần kiểm mật khẩu (email lạ kiểm với một giá trị băm giả), để thời
 * gian trả lời không phân biệt. Chỉ khi mật khẩu đúng mới lộ tài khoản không hoạt động. Quyết định gộp lỗi nằm ở đây, không
 * ở aggregate nào (aggregate chỉ trả đúng hoặc sai).
 *
 * <p>{@link LoginFailedEvent} chỉ ghi khi tìm ra tài khoản (INV-IDN-03), vào danh sách event của {@link UserAccount} và chỉ
 * mang bản băm mã thiết bị. Ở C0 event chưa được gửi đi đâu. Khi có hộp thư event, use case phải giữ được event này dù
 * ném lỗi (không quay lui giao dịch cùng event, ví dụ ghi trong giao dịch riêng).
 */
@Slf4j
@Service
public class AuthenticateWithPassword
        implements CommandHandler<AuthenticateWithPassword.Command, AuthenticateWithPassword.Result> {

    /** Mật khẩu giả chỉ để có giá trị băm giả cho email lạ; hợp lệ theo luật mật khẩu. */
    private static final String DUMMY_PASSWORD = "DummyPassw0rd";

    private static final String REASON_WRONG_PASSWORD     = "WRONG_PASSWORD";
    private static final String REASON_ACCOUNT_NOT_ACTIVE = "ACCOUNT_NOT_ACTIVE";

    /**
     * @param deviceKey bản băm mã thiết bị (cookie {@code DEVICE_KEY}), có thể {@code null} (chưa có cookie); băm ở biên của
     *                  yêu cầu nên mã thô không vào use case
     */
    public record Command(String email, String password, DeviceKeyHash deviceKey, String ipAddress, String userAgent) {}

    public record Result(UserAccountId userAccountId, Role role) {}

    private final UserAccountRepository userAccountRepository;
    private final PasswordHasher        passwordHasher;
    private final Clock                 clock;
    private final PasswordHash          dummyHash;

    public AuthenticateWithPassword(UserAccountRepository userAccountRepository, PasswordHasher passwordHasher, Clock clock) {
        this.userAccountRepository = userAccountRepository;
        this.passwordHasher        = passwordHasher;
        this.clock                 = clock;
        this.dummyHash             = passwordHasher.hash(RawPassword.of(DUMMY_PASSWORD));
    }

    @Override
    public Result handle(Command command) {
        Instant       now       = clock.instant();
        DeviceKeyHash deviceKey = command.deviceKey();
        LoginContext  context   = new LoginContext(command.ipAddress(), command.userAgent());

        Optional<UserAccount> found = findAccount(command.email());
        if (found.isEmpty()) {
            // Prevent user enumeration attack base on response time
            passwordHasher.verify(command.password(), dummyHash);
            throw UserAccountException.invalidCredentials();
        }

        UserAccount account = found.get();
        if (!verifyPassword(account, command.password())) {
            recordFailure(account, REASON_WRONG_PASSWORD, deviceKey, context, now);
            throw UserAccountException.invalidCredentials();
        }
        if (!account.canLogin()) {
            recordFailure(account, REASON_ACCOUNT_NOT_ACTIVE, deviceKey, context, now);
            throw UserAccountException.accountNotActive();
        }
        upgradePasswordHash(account, command.password());
        return new Result(account.getId(), account.getRole());
    }

    /**
     * KT-07: băm lại khi hệ số lưu cũ hơn mức hiện hành. Đây là việc phụ; lưu lỗi thì chỉ ghi cảnh báo và đăng nhập vẫn thành công
     * (lần sau sẽ băm lại).
     */
    private void upgradePasswordHash(UserAccount account, String attempt) {
        try {
            if (account.upgradePasswordHash(attempt, passwordHasher)) userAccountRepository.save(account);
        } catch (RuntimeException e) {
            log.warn("cannot upgrade the password hash of account {}: {}", account.getId(), e.getClass().getSimpleName());
        }
    }

    /** Email sai định dạng không thể có tài khoản nên coi như email không tồn tại (không lộ lỗi định dạng). */
    private Optional<UserAccount> findAccount(String rawEmail) {
        try {
            return userAccountRepository.findByEmail(Email.of(rawEmail).value());
        } catch (UserAccountException invalidEmail) {
            return Optional.empty();
        }
    }

    /** Mọi nhánh đều tốn đúng một lần kiểm mật khẩu, kể cả tài khoản chưa đặt mật khẩu. */
    private boolean verifyPassword(UserAccount account, String attempt) {
        if (account.getPasswordHash() == null) {
            passwordHasher.verify(attempt, dummyHash);
            return false;
        }
        return account.verifyPassword(attempt, passwordHasher);
    }

    private void recordFailure(UserAccount account, String reason, DeviceKeyHash deviceKey, LoginContext context,
                               Instant now) {
        account.addDomainEvent(new LoginFailedEvent(account.getId(), reason, deviceKey, context, now));
    }
}
