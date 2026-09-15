package vn.t3nexus.scheduler.infrastructure.adapter.service.cron;

import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import vn.t3nexus.scheduler.domain.schedule.CronCalculator;
import vn.t3nexus.scheduler.domain.schedule.ScheduleException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Implementation duy nhất trong toàn service còn đụng tới {@code org.springframework.scheduling.support}
 * — mọi chỗ khác trong domain (kể cả {@code RecurringSchedule}) chỉ biết interface
 * {@link CronCalculator}. Đổi cron engine sau này (nếu cần) chỉ sửa đúng file này.
 */
@Component
public class SpringCronCalculatorAdapter implements CronCalculator {

    @Override
    public Optional<Instant> nextFireTime(String cron, ZoneId zone, Instant after) {
        ZonedDateTime next;
        try {
            next = CronExpression.parse(cron).next(after.atZone(zone));
        } catch (IllegalArgumentException e) {
            // Cú pháp sai lộ ra ở đây (lần gọi đầu, thường ngay trong ScheduledJob.create()) — dịch
            // lại thành domain exception thay vì để leak IllegalArgumentException của Spring ra ngoài.
            throw ScheduleException.invalidCronExpression(cron);
        }
        return Optional.ofNullable(next).map(ZonedDateTime::toInstant);
    }
}
