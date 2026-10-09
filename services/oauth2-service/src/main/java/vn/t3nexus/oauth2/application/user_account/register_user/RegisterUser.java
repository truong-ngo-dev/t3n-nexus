package vn.t3nexus.oauth2.application.user_account.register_user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
// OUT-OF-SCOPE (hộp thư event, bật khi chặng có bên nhận event cần đến): import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.service.IdGenerator;
import vn.t3nexus.lib.observability.logging.LogContext;
import vn.t3nexus.oauth2.domain.user_account.Email;
import vn.t3nexus.oauth2.domain.user_account.PasswordHash;
import vn.t3nexus.oauth2.domain.user_account.PasswordHasher;
import vn.t3nexus.oauth2.domain.user_account.RawPassword;
import vn.t3nexus.oauth2.domain.user_account.UserAccount;
import vn.t3nexus.oauth2.domain.user_account.UserAccountId;
import vn.t3nexus.oauth2.domain.user_account.UserAccountRepository;
import vn.t3nexus.oauth2.domain.user_account.UserAccountService;

import java.time.Clock;

/**
 * Đăng ký công khai (feature 01). Vai không phải đầu vào: luôn {@code USER} (INV-ATH-06).
 * <br>Mã, giá trị băm và thời điểm được tính trước rồi truyền vào aggregate (tech.md T7).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterUser implements CommandHandler<RegisterUser.Command, RegisterUser.Result> {

    public record Command(String email, String rawPassword) {}
    public record Result(String userAccountId) {}

    private final UserAccountRepository userAccountRepository;
    private final UserAccountService    userAccountService;
    private final PasswordHasher        passwordHasher;
    private final IdGenerator           idGenerator;
    private final Clock                 clock;
    // OUT-OF-SCOPE (hộp thư event, bật khi chặng có bên nhận event cần đến): private final EventDispatcher eventDispatcher;

    @Override
    @Transactional
    public Result handle(Command command) {
        Email       email       = Email.of(command.email());
        RawPassword rawPassword = RawPassword.of(command.rawPassword());

        userAccountService.assertRegistrable(email);

        UserAccountId id           = UserAccountId.of(idGenerator.generate());
        PasswordHash  passwordHash = passwordHasher.hash(rawPassword);
        UserAccount   account      = UserAccount.register(id, email, passwordHash, clock.instant());

        // Hai yêu cầu cùng email cùng qua assertRegistrable thì ràng buộc duy nhất ở cơ sở dữ liệu là chốt cuối;
        // kho dịch vi phạm đó thành EmailTaken (INV-ATH-01).
        userAccountRepository.save(account);

        // OUT-OF-SCOPE (hộp thư event cùng giao dịch, bật khi chặng có bên nhận event cần đến):
        // eventDispatcher.dispatchAll(account.getDomainEvents());

        LogContext.user(id.getValueAsString());
        log.info("registered userAccountId={}", id);

        return new Result(id.getValueAsString());
    }
}
