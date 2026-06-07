package vn.t3nexus.oauth2.infrastructure.adapter.repository.session;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.oauth2.domain.session.OAuthSession;
import vn.t3nexus.oauth2.domain.session.OAuthSessionId;
import vn.t3nexus.oauth2.domain.session.OAuthSessionRepository;
import vn.t3nexus.oauth2.infrastructure.persistence.session.OAuthSessionJpaRepository;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OAuthSessionRepositoryAdapter implements OAuthSessionRepository {

    private final OAuthSessionJpaRepository jpaRepository;
    private final OAuthSessionMapper        mapper;

    @Override
    public boolean existsById(OAuthSessionId id) {
        return jpaRepository.existsById(id.getValueAsString());
    }

    @Override
    public Optional<OAuthSession> findActiveByIdpSessionAndClient(String idpSessionId, String registeredClientId) {
        return jpaRepository.findByIdpSessionIdAndRegisteredClientId(idpSessionId, registeredClientId)
                .map(mapper::toDomain);
    }

    @Override
    public List<OAuthSession> findAllByIdpSessionId(String idpSessionId) {
        return jpaRepository.findAllByIdpSessionId(idpSessionId)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<OAuthSession> findById(OAuthSessionId id) {
        return jpaRepository.findById(id.getValueAsString())
                .map(mapper::toDomain);
    }

    @Override
    public Optional<OAuthSession> findByAuthorizationId(String authorizationId) {
        return jpaRepository.findByAuthorizationId(authorizationId)
                .map(mapper::toDomain);
    }

    @Override
    public void save(OAuthSession session) {
        jpaRepository.upsert(
                session.getId().getValueAsString(),
                session.getUserId().getValueAsString(),
                session.getIdpSessionId(),
                session.getAuthorizationId(),
                session.getIpAddress(),
                session.getRegisteredClientId(),
                session.getCreatedAt()
        );
    }

    @Override
    public void delete(OAuthSessionId id) {
        jpaRepository.deleteById(id.getValueAsString());
    }
}
