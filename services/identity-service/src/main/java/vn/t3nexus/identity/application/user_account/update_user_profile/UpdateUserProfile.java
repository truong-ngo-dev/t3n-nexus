package vn.t3nexus.identity.application.user_account.update_user_profile;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.identity.domain.user_account.UserAccount;
import vn.t3nexus.identity.domain.user_account.UserAccountException;
import vn.t3nexus.identity.domain.user_account.UserAccountRepository;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.vo.UserId;

@Service
@RequiredArgsConstructor
public class UpdateUserProfile implements CommandHandler<UpdateUserProfile.Command, UpdateUserProfile.Result> {

    public record Command(String userId, String fullName, String phoneNumber) {}

    public record Result(String userId, String fullName, String email, String phoneNumber, String avatarUrl) {}

    private final UserAccountRepository userAccountRepository;

    @Override
    @Transactional
    public Result handle(Command command) {
        UserAccount account = userAccountRepository.findById(UserId.of(command.userId()))
                .orElseThrow(UserAccountException::notFound);
        account.updateProfile(command.fullName(), command.phoneNumber());
        userAccountRepository.save(account);
        return new Result(
                account.getId().getValue(),
                account.getFullName(),
                account.getEmail(),
                account.getPhoneNumber(),
                account.getAvatarUrl()
        );
    }
}
