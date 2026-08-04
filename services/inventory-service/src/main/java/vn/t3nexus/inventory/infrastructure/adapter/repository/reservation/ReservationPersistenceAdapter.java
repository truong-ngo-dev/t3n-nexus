package vn.t3nexus.inventory.infrastructure.adapter.repository.reservation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.inventory.domain.reservation.Reservation;
import vn.t3nexus.inventory.domain.reservation.ReservationId;
import vn.t3nexus.inventory.domain.reservation.ReservationRepository;
import vn.t3nexus.inventory.infrastructure.persistence.reservation.ReservationItemJpaEntity;
import vn.t3nexus.inventory.infrastructure.persistence.reservation.ReservationItemJpaRepository;
import vn.t3nexus.inventory.infrastructure.persistence.reservation.ReservationJpaRepository;
import vn.t3nexus.inventory.infrastructure.persistence.reservation.ReservationMapper;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ReservationPersistenceAdapter implements ReservationRepository {

    private final ReservationJpaRepository jpaRepository;
    private final ReservationItemJpaRepository itemJpaRepository;

    @Override
    public Optional<Reservation> findById(ReservationId id) {
        String rawId = id.getValue();
        return jpaRepository.findById(rawId).map(entity -> {
            List<ReservationItemJpaEntity> items = itemJpaRepository.findByReservationId(rawId);
            return ReservationMapper.toDomain(entity, items);
        });
    }

    @Override
    public Optional<Reservation> findByOrderId(String orderId) {
        return jpaRepository.findByOrderId(orderId).map(entity -> {
            List<ReservationItemJpaEntity> items = itemJpaRepository.findByReservationId(entity.getId());
            return ReservationMapper.toDomain(entity, items);
        });
    }

    @Override
    public boolean existsByOrderId(String orderId) {
        return jpaRepository.existsByOrderId(orderId);
    }

    @Override
    @Transactional
    public void save(Reservation reservation) {
        String rawId = reservation.getId().getValue();
        boolean isNew = !jpaRepository.existsById(rawId);

        // saveAndFlush forces the INSERT (and UNIQUE(order_id) check) to run inside this call,
        // so a concurrent-duplicate conflict surfaces here instead of silently at commit time —
        // callers (e.g. ReserveInventory) need to catch DataIntegrityViolationException synchronously.
        jpaRepository.saveAndFlush(ReservationMapper.toJpaEntity(reservation));

        if (isNew) {
            List<ReservationItemJpaEntity> itemEntities = reservation.getItems().stream()
                    .map(item -> ReservationMapper.toItemJpaEntity(rawId, item))
                    .toList();
            if (!itemEntities.isEmpty()) {
                itemJpaRepository.saveAll(itemEntities);
            }
        }
    }

    @Override
    public void delete(ReservationId id) {
        jpaRepository.deleteById(id.getValue());
    }
}
