package vn.t3nexus.identity.application.user_account.upload_avatar;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.identity.domain.user_account.UserAccount;
import vn.t3nexus.identity.domain.user_account.UserAccountException;
import vn.t3nexus.identity.domain.user_account.UserAccountRepository;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.vo.UserId;

import java.io.InputStream;

@Service
@RequiredArgsConstructor
public class UploadAvatar implements CommandHandler<UploadAvatar.Command, UploadAvatar.Result> {

    public record Command(String userId, InputStream content, String contentType, long size) {}

    public record Result(String avatarUrl) {}

    private final UserAccountRepository userAccountRepository;
    private final AvatarStorage         avatarStorage;

    @Override
    @Transactional
    public Result handle(Command command) {
        UserAccount account = userAccountRepository.findById(UserId.of(command.userId()))
                .orElseThrow(UserAccountException::notFound);

        String oldAvatarUrl = account.getAvatarUrl();
        String newAvatarUrl = avatarStorage.upload(
                command.userId(), command.content(), command.contentType(), command.size()
        );

        account.updateAvatar(newAvatarUrl);
        userAccountRepository.save(account);

        if (oldAvatarUrl != null) {
            avatarStorage.delete(oldAvatarUrl);
        }

        return new Result(newAvatarUrl);
    }
}
