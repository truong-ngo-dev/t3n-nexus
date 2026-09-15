package vn.t3nexus.scheduler.domain.scheduled_job;

import java.util.List;

/**
 * Port đọc phục vụ Admin List/Search UI — tách khỏi {@link ScheduledJobRepository} (2026-09-15, rà lại
 * theo góp ý): phân trang/đếm/lọc là <b>read-model concern</b>, không phải "quản lý vòng đời aggregate"
 * mà {@code Repository<T,ID>} đại diện (không 1 flow nghiệp vụ nào — Start/Stop/Edit/Fire — cần
 * {@link #search}; chỉ Admin UI liệt kê job mới cần).
 *
 * <p>{@code findById} đơn lẻ (dùng bởi {@code GetScheduledJob}) KHÔNG chuyển sang đây — nó vẫn ở
 * {@link ScheduledJobRepository}: load đúng 1 aggregate để hiển thị vẫn đúng bản chất repository, khác
 * hẳn "tìm nhiều job khớp điều kiện lọc".
 *
 * <p>Trả về {@link ScheduledJob} (aggregate thật), KHÔNG phải DTO phẳng riêng — làm phẳng
 * ({@code Schedule} đa hình → field rời rạc cho response) là việc của tầng application
 * ({@code ScheduledJobView}), không nhân đôi logic đó ở đây.
 */
public interface ScheduledJobQueryPort {

    /** Tìm job khớp {@code filter}, phân trang, sort {@code createdAt} giảm dần (mới nhất trước). */
    List<ScheduledJob> search(ScheduledJobQueryFilter filter, int page, int size);

    /** Tổng số job khớp {@code filter} — đi cặp với {@link #search}, cùng điều kiện lọc. */
    long count(ScheduledJobQueryFilter filter);
}
