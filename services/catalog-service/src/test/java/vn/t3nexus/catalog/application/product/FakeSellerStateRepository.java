package vn.t3nexus.catalog.application.product;

import vn.t3nexus.catalog.domain.product.SellerState;
import vn.t3nexus.catalog.domain.product.SellerStateRepository;
import vn.t3nexus.catalog.domain.product.SellerStatus;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Kho trạng thái người bán trong bộ nhớ cho test use case; ghi đè theo phiên bản như bản thật. */
public class FakeSellerStateRepository implements SellerStateRepository {

    private final Map<UUID, SellerState> store = new LinkedHashMap<>();

    public void approve(UUID accountId) {
        store.put(accountId, new SellerState(accountId, SellerStatus.APPROVED, 1L));
    }

    public void reject(UUID accountId) {
        store.put(accountId, new SellerState(accountId, SellerStatus.REJECTED, 1L));
    }

    @Override
    public Optional<SellerState> find(UUID accountId) {
        return Optional.ofNullable(store.get(accountId));
    }

    @Override
    public boolean applyDecision(UUID accountId, SellerStatus status, long version, Instant now) {
        SellerState current = store.get(accountId);
        if (current != null && current.version() >= version) {
            return false;
        }
        store.put(accountId, new SellerState(accountId, status, version));
        return true;
    }
}
