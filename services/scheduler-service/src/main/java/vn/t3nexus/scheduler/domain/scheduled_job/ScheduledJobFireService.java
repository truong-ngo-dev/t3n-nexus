package vn.t3nexus.scheduler.domain.scheduled_job;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.t3nexus.lib.common.domain.service.ULIDGenerator;
import vn.t3nexus.scheduler.domain.schedule.CronCalculator;
import vn.t3nexus.scheduler.domain.scheduled_job_instance.ScheduledJobInstance;
import vn.t3nexus.scheduler.domain.scheduled_job_instance.ScheduledJobInstanceId;

import java.time.Instant;

/**
 * Domain Service — điều phối {@link ScheduledJob#fire(Instant, CronCalculator)} với việc tạo
 * {@link ScheduledJobInstance} tương ứng. Cần Domain Service vì đây là bất biến nghiệp vụ liên quan 2
 * aggregate — "fire luôn tạo đúng 1 instance, snapshot taskType/payload đúng thời điểm fire" — không
 * phải orchestration thuần kỹ thuật (ddd-structure.md §Domain Service convention, tiêu chí "Logic cần
 * phối hợp nhiều aggregate").
 * <br>Cả {@link CronCalculator} lẫn {@link ULIDGenerator} đều truyền thẳng vào aggregate method
 * (Double Dispatch — xem ddd-structure.md §Double Dispatch) — service này không tự tính
 * {@code nextFireAt}/generate id, chỉ giữ port qua DI rồi giao lại cho aggregate tự quyết định.
 * {@code CronCalculator} double dispatch là bắt buộc (cần đọc {@code this.schedule}); với
 * {@code ULIDGenerator}, sinh ID không cần field nào của aggregate nên pre-compute mới là mặc định
 * hợp lý hơn ở đa số service khác — ở đây chọn double dispatch để giữ nhất quán 1 kiểu gọi cho cả 2
 * port cùng inject trong class này, không phải rule cứng (xem ddd-structure.md, mục "Trường hợp ranh
 * giới: sinh ID" cho phân tích đầy đủ).
 * <br>Không gọi Repository, không biết persistence tồn tại — Handler vẫn là nơi duy nhất `save()` cả
 * 2 aggregate (cùng transaction) + dispatch event (lấy từ {@code instance.pullEvents()}, KHÔNG phải
 * {@code scheduledJob.pullEvents()} — event giờ thuộc về {@code ScheduledJobInstance}).
 * <br>{@code @Service} theo đúng precedent thật trong repo ({@code AttributeTemplateDomainService} ở
 * catalog-service) — khác quy tắc chữ trong ddd-structure.md ("domain/ không import Spring"), nhưng
 * đây là convention thực tế đang áp dụng nhất quán cho mọi Domain Service cần là Spring bean.
 */
@Service
@RequiredArgsConstructor
public class ScheduledJobFireService {

    private final CronCalculator cronCalculator;
    private final ULIDGenerator ulidGenerator;

    /**
     * @param scheduledJob aggregate đã load + đã qua guard {@code canProcess()} ở Handler (Flow D
     *                     re-verify) — service này không tự check lại, tin caller đã verify
     * @param now          T_emit
     * @return {@code ScheduledJobInstance} mới, status {@code DISPATCHED}, đã raise
     *         {@code ScheduledJobFiredEvent} nội bộ — Handler tự save cả 2 aggregate cùng transaction
     *         + dispatch event lấy từ instance này
     */
    public ScheduledJobInstance fire(ScheduledJob scheduledJob, Instant now) {
        scheduledJob.fire(now, cronCalculator);
        return ScheduledJobInstance.dispatch(ulidGenerator, scheduledJob.getId(), scheduledJob.getTaskType(),
               scheduledJob.getPayload(), now);
    }
}
