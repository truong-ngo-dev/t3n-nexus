package vn.t3nexus.oauth2.domain.user_account;

/**
 * Domain Service: điều kiện đăng ký công khai cần kho và chính sách nên không thuộc riêng aggregate nào.
 * Không dùng chú thích khung ứng dụng; bean được khai báo ở cấu hình.
 */
public class UserAccountService {

    private final UserAccountRepository userAccountRepository;
    private final InternalEmailPolicy   internalEmailPolicy;

    public UserAccountService(UserAccountRepository userAccountRepository, InternalEmailPolicy internalEmailPolicy) {
        this.userAccountRepository = userAccountRepository;
        this.internalEmailPolicy   = internalEmailPolicy;
    }

    /**
     * Báo sớm. Chốt cuối cho "email chưa dùng" là ràng buộc duy nhất ở cơ sở dữ liệu (INV-ATH-01).
     *
     * @throws UserAccountException {@code EMAIL_RESERVED} nếu thuộc miền nội bộ, {@code EMAIL_TAKEN} nếu đã dùng
     */
    public void assertRegistrable(Email email) {
        if (internalEmailPolicy.isInternal(email)) throw UserAccountException.emailReserved();
        if (userAccountRepository.existsByEmail(email)) throw UserAccountException.emailTaken();
    }
}
