package vn.t3nexus.scheduler.domain.scheduled_job;

/**
 * Filter cho {@link ScheduledJobQueryPort} — mọi field {@code null} nghĩa là "không lọc theo tiêu chí
 * đó", AND-combined. {@code status}/{@code taskType} cố tình để {@code String} thô (không phải
 * {@link ScheduledJobStatus} enum) — filter query-param sai giá trị nên trả về rỗng (0 kết quả khớp),
 * không nên {@code throw IllegalArgumentException} rồi 500 như một enum {@code valueOf()} sẽ làm.
 *
 * <p><b>2 kiểu lọc khác nhau, cố tình không đồng nhất</b>:
 * <ul>
 *   <li>{@code status}/{@code taskType} — <b>exact match</b>, categorical (tập giá trị hữu hạn dù không
 *       whitelist). Dùng cho tra cứu kỹ thuật (VD "tất cả job wire vào CART_CLEANUP").</li>
 *   <li>{@code jobName} — <b>substring search</b> (case-insensitive, {@code LIKE '%...%'}). Đây là nhãn
 *       tự do do Admin đặt để CON NGƯỜI tìm/nhận diện job — khác hẳn {@code taskType} (routing tag cho
 *       consumer, immutable). Exact match không hợp lý cho 1 nhãn tự do.</li>
 * </ul>
 *
 * <p>3 field đủ dùng ở scope hiện tại (4 job cố định) — thêm field mới khi Admin UI thật sự cần lọc
 * thêm, không suy diễn trước.
 */
public record ScheduledJobQueryFilter(String jobName, String status, String taskType) {

    public static ScheduledJobQueryFilter empty() {
        return new ScheduledJobQueryFilter(null, null, null);
    }
}
