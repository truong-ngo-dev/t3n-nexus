package vn.t3nexus.scheduler.presentation.scheduled_job;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.t3nexus.lib.web.commons.response.ApiResponse;
import vn.t3nexus.scheduler.application.scheduled_job.create.CreateScheduledJob;
import vn.t3nexus.scheduler.application.scheduled_job.edit.EditScheduledJob;
import vn.t3nexus.scheduler.application.scheduled_job.get.GetScheduledJob;
import vn.t3nexus.scheduler.application.scheduled_job.list.ListScheduledJobs;
import vn.t3nexus.scheduler.application.scheduled_job.shared.ScheduledJobView;
import vn.t3nexus.scheduler.application.scheduled_job.start.StartScheduledJob;
import vn.t3nexus.scheduler.application.scheduled_job.stop.StopScheduledJob;
import vn.t3nexus.scheduler.presentation.scheduled_job.model.CreateScheduledJobRequest;
import vn.t3nexus.scheduler.presentation.scheduled_job.model.EditScheduledJobRequest;
import vn.t3nexus.scheduler.presentation.scheduled_job.model.ScheduledJobResponse;

import java.util.List;

/**
 * Admin-only — toàn bộ endpoint dưới {@code /api/admin/scheduled-jobs}, secure qua
 * {@code SecurityConfig} ({@code anyRequest().authenticated()}, không có nhánh public nào). Không có
 * {@code DELETE}: vòng đời job đi qua {@code stop()} (tạm dừng, khởi động lại được), không có "xoá vĩnh
 * viễn" ở tầng domain (xem service.md §Domain Model, pattern K8s CronJob {@code suspend}).
 */
@RestController
@RequiredArgsConstructor
public class ScheduledJobController {

    private final CreateScheduledJob createScheduledJob;
    private final EditScheduledJob editScheduledJob;
    private final StartScheduledJob startScheduledJob;
    private final StopScheduledJob stopScheduledJob;
    private final GetScheduledJob getScheduledJob;
    private final ListScheduledJobs listScheduledJobs;

    @PostMapping("/api/admin/scheduled-jobs")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<String> createScheduledJob(@Valid @RequestBody CreateScheduledJobRequest request) {
        CreateScheduledJob.Result result = createScheduledJob.handle(new CreateScheduledJob.Command(
                request.jobName(), request.taskType(), request.scheduleType(), request.dueAt(),
                request.cronExpression(), request.timezone(), request.payload(), request.misfireInstruction()));
        return ApiResponse.ok(result.id());
    }

    @GetMapping("/api/admin/scheduled-jobs")
    public ApiResponse<ScheduledJobResponse.PagedResponse> listScheduledJobs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String jobName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String taskType) {
        ListScheduledJobs.Result result = listScheduledJobs.handle(
                new ListScheduledJobs.Query(page, size, jobName, status, taskType));
        List<ScheduledJobResponse> items = result.items().stream().map(ScheduledJobController::toResponse).toList();
        return ApiResponse.ok(new ScheduledJobResponse.PagedResponse(items, result.total(), page, size));
    }

    @GetMapping("/api/admin/scheduled-jobs/{id}")
    public ApiResponse<ScheduledJobResponse> getScheduledJob(@PathVariable String id) {
        ScheduledJobView view = getScheduledJob.handle(new GetScheduledJob.Query(id));
        return ApiResponse.ok(toResponse(view));
    }

    @PutMapping("/api/admin/scheduled-jobs/{id}")
    public ApiResponse<Void> editScheduledJob(@PathVariable String id,
                                              @Valid @RequestBody EditScheduledJobRequest request) {
        editScheduledJob.handle(new EditScheduledJob.Command(
                id, request.jobName(), request.scheduleType(), request.dueAt(), request.cronExpression(),
                request.timezone(), request.payload(), request.misfireInstruction()));
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/admin/scheduled-jobs/{id}/start")
    public ApiResponse<Void> startScheduledJob(@PathVariable String id) {
        startScheduledJob.handle(new StartScheduledJob.Command(id));
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/admin/scheduled-jobs/{id}/stop")
    public ApiResponse<Void> stopScheduledJob(@PathVariable String id) {
        stopScheduledJob.handle(new StopScheduledJob.Command(id));
        return ApiResponse.ok(null);
    }

    private static ScheduledJobResponse toResponse(ScheduledJobView view) {
        return new ScheduledJobResponse(
                view.id(), view.jobName(), view.taskType(), view.scheduleType(), view.dueAt(),
                view.cronExpression(), view.timezone(), view.payload(), view.status(), view.nextFireAt(),
                view.misfireInstruction(), view.createdAt(), view.updatedAt());
    }
}
