package vn.t3nexus.lib.observability.logging;

import org.springframework.core.task.TaskDecorator;

/**
 * Truyền ngữ cảnh log (MDC) của luồng giao việc sang luồng thực thi. Gắn vào executor do Spring quản lý
 * ({@code ThreadPoolTaskExecutor#setTaskDecorator}) để {@code @Async} giữ được ngữ cảnh của nhánh gọi.
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        return LogContext.wrap(runnable);
    }
}
