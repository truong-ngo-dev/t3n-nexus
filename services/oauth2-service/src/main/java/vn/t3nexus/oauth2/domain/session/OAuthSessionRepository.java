package vn.t3nexus.oauth2.domain.session;

import vn.t3nexus.lib.common.domain.service.Repository;

import java.util.List;
import java.util.Optional;

public interface OAuthSessionRepository extends Repository<OAuthSession, OAuthSessionId> {

    boolean existsById(OAuthSessionId id);

    Optional<OAuthSession> findActiveByIdpSessionAndClient(String idpSessionId, String registeredClientId);

    List<OAuthSession> findAllByIdpSessionId(String idpSessionId);

    Optional<OAuthSession> findByAuthorizationId(String authorizationId);
}
