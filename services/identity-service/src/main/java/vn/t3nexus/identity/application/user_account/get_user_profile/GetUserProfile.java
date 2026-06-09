package vn.t3nexus.identity.application.user_account.get_user_profile;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.t3nexus.identity.domain.user_account.UserAccount;
import vn.t3nexus.identity.domain.user_account.UserAccountException;
import vn.t3nexus.identity.domain.user_account.UserAccountRepository;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;
import vn.t3nexus.lib.common.domain.vo.UserId;

@Service
@RequiredArgsConstructor
public class GetUserProfile implements QueryHandler<GetUserProfile.Query, GetUserProfile.Result> {

    public record Query(String userId) {}

    public record Result(String userId, String fullName, String email, String phoneNumber, String avatarUrl) {}

    private final UserAccountRepository userAccountRepository;

    @Override
    public Result handle(Query query) {
        UserAccount account = userAccountRepository.findById(UserId.of(query.userId()))
                .orElseThrow(UserAccountException::notFound);
        return new Result(
                account.getId().getValue(),
                account.getFullName(),
                account.getEmail(),
                account.getPhoneNumber(),
                account.getAvatarUrl()
        );
    }
}
