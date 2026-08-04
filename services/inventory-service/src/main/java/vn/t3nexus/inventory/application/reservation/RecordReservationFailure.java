package vn.t3nexus.inventory.application.reservation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import vn.t3nexus.inventory.domain.reservation.Reservation;
import vn.t3nexus.inventory.domain.reservation.ReservationId;
import vn.t3nexus.inventory.domain.reservation.ReservationItem;
import vn.t3nexus.inventory.domain.reservation.ReservationItemId;
import vn.t3nexus.inventory.domain.reservation.ReservationRepository;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.service.ULIDGenerator;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecordReservationFailure {

    private final ReservationRepository reservationRepository;
    private final EventDispatcher eventDispatcher;
    private final ULIDGenerator ulidGenerator;

    /** T2 — runs in a NEW transaction after T1 has rolled back. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(Command command) {
        List<ReservationItem> items = command.items().stream()
                .map(item -> ReservationItem.create(
                        ReservationItemId.of(ulidGenerator.generate()), item.skuId(), item.qty()))
                .toList();

        ReservationId id = ReservationId.of(ulidGenerator.generate());
        Reservation reservation = Reservation.createFailed(
                id, command.orderId(), items,
                command.failedSkuId(), command.reason());

        try {
            reservationRepository.save(reservation);
        } catch (DataIntegrityViolationException e) {
            // Concurrent/duplicate: a Reservation for this orderId already exists (UNIQUE(order_id)).
            // Benign no-op, not a real failure — no Kafka retry/DLQ needed.
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            log.info("[RecordReservationFailure] duplicate orderId={} (concurrent), skipping", command.orderId());
            return;
        }
        eventDispatcher.dispatchAll(reservation.getDomainEvents());
        reservation.clearDomainEvents();

        log.info("[RecordReservationFailure] recorded: reservationId={}, orderId={}, failedSkuId={}, traceId={}",
                id.getValue(), command.orderId(), command.failedSkuId(), MDC.get("traceId"));
    }

    public record Command(String orderId, List<Item> items, String failedSkuId, String reason) {
        public record Item(String skuId, int qty) {}
    }
}
