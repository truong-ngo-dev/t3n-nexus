package vn.t3nexus.oauth2.application.user_account.get_password_status;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;
import vn.t3nexus.oauth2.domain.user_account.UserAccountId;
import vn.t3nexus.oauth2.domain.user_account.UserAccountException;
import vn.t3nexus.oauth2.domain.user_account.UserAccountRepository;

@Service
@RequiredArgsConstructor
public class GetPasswordStatus implements QueryHandler<GetPasswordStatus.Query, GetPasswordStatus.Result> {

    public record Query(String userId) {}
    public record Result(boolean hasPassword) {}

    private final UserAccountRepository credentialRepository;

    @Override
    public Result handle(Query query) {
        boolean hasPassword = credentialRepository.findById(UserAccountId.of(query.userId()))
                .orElseThrow(UserAccountException::notFound)
                .hasPassword();
        return new Result(hasPassword);
    }
}
