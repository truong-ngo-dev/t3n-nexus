package vn.t3nexus.oauth2.domain.session;

import vn.t3nexus.lib.common.domain.service.Repository;

import java.util.Optional;

public interface OAuthSessionRepository extends Repository<OAuthSession, OAuthSessionId> {

    Optional<OAuthSession> findByAuthorizationId(String authorizationId);
}
