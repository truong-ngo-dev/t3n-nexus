package vn.t3nexus.inventory.application.reservation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.t3nexus.inventory.domain.reservation.ReservationRepository;

import java.time.Instant;
import java.util.List;

/**
 * Lớp 2 (backstop) của TTL self-guard trên {@code Reservation} — xử lý 1 lần "đánh thức" từ
 * {@code taskType=RESERVATION_TIMEOUT_DB_SCAN}. Chỉ bắt được reservation bị Lớp 1 (Redis) miss.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScanDbReservationTimeout {

    private static final int BATCH_LIMIT = 100;

    private final ReservationRepository reservationRepository;
    private final ReleaseReservation releaseReservation;

    public Result handle(Command command) {
        List<String> dueOrderIds = reservationRepository.findOrderIdsPendingWithExpiredDeadline(Instant.now(), BATCH_LIMIT);
        dueOrderIds.forEach(orderId -> releaseReservation.handle(new ReleaseReservation.Command(orderId)));
        if (!dueOrderIds.isEmpty()) {
            log.info("[ScanDbReservationTimeout] released {} reservation(s) missed by Lớp 1 (Redis): {}", dueOrderIds.size(), dueOrderIds);
        }
        return new Result(dueOrderIds.size());
    }

    public record Command() {}

    public record Result(int releasedCount) {}
}
