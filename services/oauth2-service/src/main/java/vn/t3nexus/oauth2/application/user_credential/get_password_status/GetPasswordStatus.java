package vn.t3nexus.oauth2.application.user_credential.get_password_status;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;
import vn.t3nexus.lib.common.domain.vo.UserId;
import vn.t3nexus.oauth2.domain.user_credential.UserCredentialException;
import vn.t3nexus.oauth2.domain.user_credential.UserCredentialRepository;

@Service
@RequiredArgsConstructor
public class GetPasswordStatus implements QueryHandler<GetPasswordStatus.Query, GetPasswordStatus.Result> {

    public record Query(String userId) {}
    public record Result(boolean hasPassword) {}

    private final UserCredentialRepository credentialRepository;

    @Override
    public Result handle(Query query) {
        boolean hasPassword = credentialRepository.findById(UserId.of(query.userId()))
                .orElseThrow(UserCredentialException::notFound)
                .hasPassword();
        return new Result(hasPassword);
    }
}
