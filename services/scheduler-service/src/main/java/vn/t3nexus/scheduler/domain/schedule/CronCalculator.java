package vn.t3nexus.scheduler.domain.schedule;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Port kỹ thuật thuần — không thuộc về aggregate nào cả, cùng shape với {@code ULIDGenerator} trong
 * {@code common-domain}. Tách khỏi {@link RecurringSchedule} để domain không phụ thuộc trực tiếp vào
 * thư viện parse cron cụ thể (hiện implement bằng Spring's {@code CronExpression}, xem
 * {@code infrastructure/adapter/service/cron/SpringCronCalculatorAdapter}).
 * <br><b>Vì sao truyền thẳng vào {@code Schedule.nextFireTime()} thay vì resolve trước ở Handler kiểu
 * {@code CollaboratorService}</b>: đây là Strategy thuần — deterministic, không I/O, không state,
 * không băng qua bounded-context nào (khác {@code AssigneeService}, vốn có I/O + cross-BC nên bắt buộc
 * resolve trước). Truyền thẳng port vào giữ được tính đa hình của {@code Schedule}
 * ({@link OneOffSchedule} bỏ qua tham số này hoàn toàn), tránh tái sinh nhánh {@code if (recurring)}
 * ở tầng gọi.
 */
@FunctionalInterface
public interface CronCalculator {
    Optional<Instant> nextFireTime(String cron, ZoneId zone, Instant after);
}
