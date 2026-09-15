package vn.t3nexus.scheduler.domain.scheduled_job;

import vn.t3nexus.lib.common.domain.service.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Aggregate life-cycle port — {@code findById}/{@code save}/{@code delete} (từ {@link Repository}) +
 * 2 method dưới đây, cả 2 vẫn thuộc đúng bản chất "load 1 aggregate để dùng cho 1 flow nghiệp vụ", không
 * phải reporting. <b>Phân trang/đếm/lọc cho Admin List UI KHÔNG đặt ở đây</b> — xem
 * {@link ScheduledJobQueryPort}: đó là read-model/search concern, không phải quản lý vòng đời aggregate
 * mà interface này đại diện (tách ra sau khi rà lại, 2026-09-15).
 */
public interface ScheduledJobRepository extends Repository<ScheduledJob, ScheduledJobId> {

    /**
     * Tìm tối đa {@code limit} job đang RUNNING có {@code nextFireAt <= asOf}, kèm {@code nextFireAt}
     * (xem {@link DueJobRef}), sắp xếp {@code nextFireAt} tăng dần. Nơi gọi duy nhất:
     * {@code IndexReconciler} — quét định kỳ rồi gọi {@link DueItemFinder#index} cho từng row để nạp lại
     * index (bù drift của nhánh event-driven). <b>KHÔNG fire ở đây</b>: nguồn Claim+Trigger duy nhất là
     * {@code IndexPoller} đọc {@link DueItemFinder#pollDue}, rồi re-verify qua
     * {@link #findByIdForUpdate(ScheduledJobId)} (Flow D, Knowledge base {@code 5. core-components.md}).
     * Trả về không kèm lock — chỉ dùng nạp index, xem javadoc {@code IndexReconciler} phần concurrent.
     */
    List<DueJobRef> findDueBefore(Instant asOf, int limit);

    /**
     * Load + row-lock (SELECT ... FOR UPDATE) — kết hợp Claim + Re-verify thành 1 round-trip DB, không
     * cần bảng lock riêng kiểu {@code QRTZ_LOCKS} của Quartz (xem service.md Business Rules).
     */
    Optional<ScheduledJob> findByIdForUpdate(ScheduledJobId id);
}
