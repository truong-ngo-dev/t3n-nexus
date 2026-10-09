package vn.t3nexus.inventory.domain.reservation;

import java.time.Instant;
import java.util.List;

/**
 * Lớp 1 (tốc độ) của TTL self-guard trên {@link Reservation} — Redis ZSET, <b>không phải nguồn sự
 * thật</b> (đó là cột {@code reservation.expires_at}, Lớp 2 backstop). Key = {@code orderId}, khớp
 * tham số {@code ReleaseReservation.Command} — tránh 1 tầng gián tiếp reservationId↔orderId không cần
 * thiết. Implementation phải best-effort cho {@code add}/{@code remove}.
 */
public interface ReservationTimeoutIndex {
    void add(String orderId, Instant expiresAt);
    void remove(String orderId);

    /** Claim atomic (pop) các orderId đã quá hạn, tối đa {@code limit} phần tử. */
    List<String> pollDue(Instant asOf, int limit);
}
