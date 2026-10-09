package vn.t3nexus.inventory.application.reservation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.inventory.domain.reservation.Reservation;
import vn.t3nexus.inventory.domain.reservation.ReservationRepository;
import vn.t3nexus.inventory.domain.reservation.ReservationTimeoutIndex;

/**
 * Consume {@code OrderConfirmed} — chuyển {@link Reservation} sang {@code COMMITTED}, dừng đồng hồ TTL
 * vĩnh viễn (xem javadoc {@code Reservation#create}). Idempotent tự nhiên qua {@code isPending()} —
 * redelivery không gây lỗi, chỉ no-op.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommitReservation {

    private final ReservationRepository reservationRepository;
    private final ReservationTimeoutIndex reservationTimeoutIndex;

    @Transactional
    public void handle(Command command) {
        // findByOrderIdForUpdate — cùng lý do khoá pessimistic đã áp dụng ở ReleaseReservation: chặn
        // race nếu OrderConfirmed và 1 lần release nào đó (không nên xảy ra cùng lúc, nhưng phòng thủ).
        Reservation reservation = reservationRepository.findByOrderIdForUpdate(command.orderId()).orElse(null);

        if (reservation == null) {
            log.info("[CommitReservation] no reservation found for orderId={}, no-op", command.orderId());
            return;
        }

        if (!reservation.isPending()) {
            log.info("[CommitReservation] reservation orderId={} is already {}, no-op",
                    command.orderId(), reservation.getStatus());
            return;
        }

        reservation.commit();
        reservationRepository.save(reservation);
        reservationTimeoutIndex.remove(command.orderId());

        log.info("[CommitReservation] committed: reservationId={}, orderId={}, traceId={}",
                reservation.getId().getValue(), command.orderId(), MDC.get("traceId"));
    }

    public record Command(String orderId) {}
}
