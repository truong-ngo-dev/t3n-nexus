package vn.t3nexus.catalog.infrastructure.adapter.repository.sellerstate;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.product.SellerState;
import vn.t3nexus.catalog.domain.product.SellerStateRepository;
import vn.t3nexus.catalog.domain.product.SellerStatus;
import vn.t3nexus.catalog.infrastructure.persistence.sellerstate.SellerStateJpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SellerStatePersistenceAdapter implements SellerStateRepository {

    private final SellerStateJpaRepository states;

    @Override
    @Transactional(readOnly = true)
    public Optional<SellerState> find(UUID accountId) {
        return states.findById(accountId)
                .map(e -> new SellerState(e.getAccountId(), e.getStatus(), e.getVersion()));
    }

    @Override
    @Transactional
    public boolean applyDecision(UUID accountId, SellerStatus status, long version, Instant now) {
        return states.applyDecision(accountId, status.name(), version, now) > 0;
    }
}
