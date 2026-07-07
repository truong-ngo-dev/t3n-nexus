package vn.t3nexus.inventory.application.reservation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.inventory.domain.reservation.Reservation;
import vn.t3nexus.inventory.domain.reservation.ReservationId;
import vn.t3nexus.inventory.domain.reservation.ReservationItem;
import vn.t3nexus.inventory.domain.reservation.ReservationItemId;
import vn.t3nexus.inventory.domain.reservation.ReservationRepository;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.service.ULIDGenerator;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecordReservationFailure {

    private static final Duration RESERVATION_TTL = Duration.ofMinutes(30);

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
                Instant.now().plus(RESERVATION_TTL),
                command.failedSkuId(), command.reason());

        reservationRepository.save(reservation);
        eventDispatcher.dispatchAll(reservation.getDomainEvents());
        reservation.clearDomainEvents();

        log.info("[RecordReservationFailure] recorded: reservationId={}, orderId={}, failedSkuId={}, traceId={}",
                id.getValue(), command.orderId(), command.failedSkuId(), MDC.get("traceId"));
    }

    public record Command(String orderId, List<Item> items, String failedSkuId, String reason) {
        public record Item(String skuId, int qty) {}
    }
}
