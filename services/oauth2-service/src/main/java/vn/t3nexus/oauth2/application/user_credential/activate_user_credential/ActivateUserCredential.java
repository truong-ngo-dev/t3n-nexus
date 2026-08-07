package vn.t3nexus.oauth2.application.user_credential.activate_user_credential;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.vo.UserId;
import vn.t3nexus.oauth2.domain.user_credential.UserCredential;
import vn.t3nexus.oauth2.domain.user_credential.UserCredentialException;
import vn.t3nexus.oauth2.domain.user_credential.UserCredentialRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivateUserCredential implements CommandHandler<ActivateUserCredential.Command, ActivateUserCredential.Result> {

    public record Command(String userId) {}

    public record Result(String userId, String status) {}

    private final UserCredentialRepository credentialRepository;

    @Override
    @Transactional
    public Result handle(Command command) {
        UserCredential credential = credentialRepository.findById(UserId.of(command.userId()))
                .orElseThrow(UserCredentialException::notFound);

        // Idempotent theo thiết kế, không strict như UserCredential.activate(): Kafka at-least-once
        // có thể redeliver UserActivated. Nếu credential đã ACTIVE (đã xử lý trước đó) hoặc đã LOCKED
        // (admin khoá sau khi event gốc phát ra) -- no-op, không throw. Đặc biệt quan trọng cho case
        // LOCKED: một UserActivated cũ bị trễ không được phép ghi đè quyết định khoá tài khoản.
        if (credential.isPending()) {
            credential.activate();
            credentialRepository.save(credential);
            log.info("[ActivateUserCredential] activated: userId={}, traceId={}", command.userId(), MDC.get("traceId"));
        } else {
            // Đáng log nhất trong 2 nhánh — báo hiệu 1 UserActivated event trễ/redeliver đến sau
            // khi credential đã đổi trạng thái (ACTIVE do xử lý trước, hoặc LOCKED do admin can
            // thiệp). Trước đây im lặng hoàn toàn, không cách nào phát hiện qua log case LOCKED bị
            // 1 event cũ cố ghi đè (dù code đã chặn đúng, vẫn nên biết việc này xảy ra).
            log.info("[ActivateUserCredential] skip no-op: userId={}, currentStatus={}, traceId={}",
                    command.userId(), credential.getStatus(), MDC.get("traceId"));
        }

        return new Result(credential.getId().getValue(), credential.getStatus().name());
    }
}
