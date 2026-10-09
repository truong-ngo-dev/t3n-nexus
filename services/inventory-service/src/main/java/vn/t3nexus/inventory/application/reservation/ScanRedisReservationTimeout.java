package vn.t3nexus.inventory.application.reservation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.t3nexus.inventory.domain.reservation.ReservationTimeoutIndex;

import java.time.Instant;
import java.util.List;

/**
 * Lớp 1 (tốc độ) của TTL self-guard trên {@code Reservation} — xử lý 1 lần "đánh thức" từ
 * {@code taskType=RESERVATION_TIMEOUT_REDIS_SCAN}. Mỗi orderId quá hạn đi qua
 * {@link ReleaseReservation#handle}, tự no-op nếu đã {@code COMMITTED}/{@code RELEASED}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScanRedisReservationTimeout {

    private static final int BATCH_LIMIT = 100;

    private final ReservationTimeoutIndex reservationTimeoutIndex;
    private final ReleaseReservation releaseReservation;

    public Result handle(Command command) {
        List<String> dueOrderIds = reservationTimeoutIndex.pollDue(Instant.now(), BATCH_LIMIT);
        dueOrderIds.forEach(orderId -> releaseReservation.handle(new ReleaseReservation.Command(orderId)));
        if (!dueOrderIds.isEmpty()) {
            log.info("[ScanRedisReservationTimeout] released {} reservation(s): {}", dueOrderIds.size(), dueOrderIds);
        }
        return new Result(dueOrderIds.size());
    }

    public record Command() {}

    public record Result(int releasedCount) {}
}
