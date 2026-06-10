package vn.t3nexus.identity.infrastructure.adapter.repository.login_activity;

import org.springframework.stereotype.Component;
import vn.t3nexus.identity.domain.login_activity.LoginActivity;
import vn.t3nexus.identity.domain.login_activity.LoginActivityId;
import vn.t3nexus.identity.domain.login_activity.LoginResult;
import vn.t3nexus.identity.infrastructure.persistence.login_activity.LoginActivityJpaEntity;
import vn.t3nexus.lib.common.domain.vo.UserId;

@Component
public class LoginActivityMapper {

    public LoginActivity toDomain(LoginActivityJpaEntity entity) {
        return LoginActivity.reconstitute(
                LoginActivityId.of(entity.getId()),
                UserId.of(entity.getUserId()),
                entity.getUsername(),
                LoginResult.valueOf(entity.getResult()),
                entity.getIpAddress(),
                entity.getUserAgent(),
                entity.getCompositeHash(),
                entity.getDeviceId(),
                entity.getSessionId(),
                LoginActivity.LoginProvider.valueOf(entity.getProvider()),
                entity.getCreatedAt(),
                entity.getEndedAt()
        );
    }
}
