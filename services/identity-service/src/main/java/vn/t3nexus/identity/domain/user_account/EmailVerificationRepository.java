package vn.t3nexus.identity.domain.user_account;

import vn.t3nexus.lib.common.domain.vo.UserId;
import vn.t3nexus.lib.common.domain.service.Repository;

import java.util.Optional;

public interface EmailVerificationRepository extends Repository<EmailVerification, EmailVerificationId> {

    Optional<EmailVerification> findByToken(String token);

    Optional<EmailVerification> findByUserId(UserId userId);

    /**
     * Insert atomically — no-op nếu {@code user_id} đã có row (constraint {@code uq_email_verifications_user_id}).
     * Dùng cho lần tạo đầu tiên (register) thay vì {@link #save}, vì {@code id} sinh ULID mới mỗi lần gọi nên
     * {@code save()} (upsert theo id) không tự chặn được duplicate khi 2 lần xử lý cùng userId race nhau.
     *
     * @return {@code true} nếu row mới thật sự được tạo, {@code false} nếu đã tồn tại (bị bỏ qua)
     */
    boolean createIfAbsent(EmailVerification verification);
}
