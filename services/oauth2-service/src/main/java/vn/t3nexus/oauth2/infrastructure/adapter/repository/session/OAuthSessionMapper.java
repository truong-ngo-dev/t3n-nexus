package vn.t3nexus.oauth2.infrastructure.adapter.repository.session;

import org.springframework.stereotype.Component;
import vn.t3nexus.lib.common.domain.vo.UserId;
import vn.t3nexus.oauth2.domain.session.OAuthSession;
import vn.t3nexus.oauth2.domain.session.OAuthSessionId;
import vn.t3nexus.oauth2.infrastructure.persistence.session.OAuthSessionJpaEntity;

@Component
public class OAuthSessionMapper {

    public OAuthSession toDomain(OAuthSessionJpaEntity entity) {
        return OAuthSession.reconstitute(
                new OAuthSessionId(entity.getId()),
                UserId.of(entity.getUserId()),
                entity.getIdpSessionId(),
                entity.getAuthorizationId(),
                entity.getIpAddress(),
                entity.getStatus(),
                entity.getCreatedAt()
        );
    }
}
