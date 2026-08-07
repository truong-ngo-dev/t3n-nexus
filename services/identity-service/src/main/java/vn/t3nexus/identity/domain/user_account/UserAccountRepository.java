package vn.t3nexus.identity.domain.user_account;

import vn.t3nexus.lib.common.domain.vo.UserId;
import vn.t3nexus.lib.common.domain.service.Repository;

import java.util.Optional;

public interface UserAccountRepository extends Repository<UserAccount, UserId> {
    Optional<UserAccount> findByEmail(String email);

    /**
     * Insert atomically — no-op nếu {@code id} (userId) đã có row. Dùng cho lần tạo đầu tiên
     * (register) thay vì {@link #save} — {@code save()} là upsert (ghi đè), không cho biết đây có
     * phải lần insert thật hay không, nên không tự gate được việc publish domain event khi race.
     *
     * @return {@code true} nếu row mới thật sự được tạo (ai đến trước thắng), {@code false} nếu đã tồn tại
     */
    boolean createIfAbsent(UserAccount userAccount);
}
