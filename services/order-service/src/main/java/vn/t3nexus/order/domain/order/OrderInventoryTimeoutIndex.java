package vn.t3nexus.order.domain.order;

import java.time.Instant;
import java.util.List;

/**
 * Lớp 1 (tốc độ) của cơ chế {@code CREATED}-timeout — Redis ZSET, <b>không phải nguồn sự thật</b>
 * (đó là cột {@code orders.inventory_reply_deadline}, Lớp 2 backstop). Implementation phải best-effort
 * cho {@code add}/{@code remove} — lỗi ghi ở đây không được chặn luồng tạo/xử lý {@link Order}.
 */
public interface OrderInventoryTimeoutIndex {
    void add(String orderId, Instant deadline);
    void remove(String orderId);

    /** Claim atomic (pop) các orderId đã quá hạn, tối đa {@code limit} phần tử — dùng bởi worker quét. */
    List<String> pollDue(Instant asOf, int limit);
}
