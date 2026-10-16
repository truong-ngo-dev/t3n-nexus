package vn.t3nexus.oauth2.infrastructure.adapter.repository.login_session;

import org.springframework.stereotype.Component;
import vn.t3nexus.oauth2.domain.login_session.ClientId;
import vn.t3nexus.oauth2.domain.login_session.ClientSession;
import vn.t3nexus.oauth2.domain.login_session.ClientSessionId;
import vn.t3nexus.oauth2.domain.login_session.DeviceKeyHash;
import vn.t3nexus.oauth2.domain.login_session.LoginSession;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionId;
import vn.t3nexus.oauth2.domain.user_account.UserAccountId;
import vn.t3nexus.oauth2.infrastructure.persistence.login_session.ClientSessionJpaEntity;
import vn.t3nexus.oauth2.infrastructure.persistence.login_session.LoginSessionJpaEntity;

import java.util.Comparator;
import java.util.List;

@Component
public class LoginSessionMapper {

    public LoginSession toDomain(LoginSessionJpaEntity entity) {
        List<ClientSession> clientSessions = entity.getClientSessions().stream()
                .sorted(Comparator.comparing(ClientSessionJpaEntity::getIssuedAt))
                .map(this::toDomain)
                .toList();
        return LoginSession.reconstitute(
                LoginSessionId.of(entity.getId()),
                UserAccountId.of(entity.getUserAccountId()),
                entity.getDeviceKey() == null ? null : new DeviceKeyHash(entity.getDeviceKey()),
                entity.getIdpSessionId(),
                entity.getIssuedAt(),
                entity.getExpiresAt(),
                clientSessions);
    }

    private ClientSession toDomain(ClientSessionJpaEntity entity) {
        return ClientSession.reconstitute(
                ClientSessionId.of(entity.getId()),
                new ClientId(entity.getClientId()),
                entity.getAuthorizationId(),
                entity.getIssuedAt());
    }
}
