-- ============================================================
-- scheduler-service — V3: scheduled_job_instance
-- ============================================================

-- ------------------------------------------------------------
-- scheduled_job_instance
-- Aggregate rieng, KHONG phai child entity cua scheduled_job (xem service.md). Append-only theo thoi
-- gian — 1 row/lan fire. Khong can @Version — khong co 2 writer dong thoi tren cung 1 row.
-- ------------------------------------------------------------
CREATE TABLE scheduled_job_instance (
    id                VARCHAR(26)    NOT NULL,
    scheduled_job_id  VARCHAR(26)    NOT NULL,
    task_type         VARCHAR(100)   NOT NULL,
    payload           JSONB          NOT NULL DEFAULT '{}',
    status            VARCHAR(20)    NOT NULL,
    fired_at          TIMESTAMPTZ    NOT NULL,
    completed_at      TIMESTAMPTZ,
    failure_reason    TEXT,

    CONSTRAINT pk_scheduled_job_instance PRIMARY KEY (id),
    CONSTRAINT ck_scheduled_job_instance_status
        CHECK (status IN ('DISPATCHED', 'SUCCEEDED', 'FAILED'))
    -- Khong co FK constraint DB cung tơi scheduled_job.id — 2 aggregate doc lap, dung aggregate
    -- boundary (xem service.md §ScheduledJobInstance)
);

-- Phuc vu query lich su run cua 1 job (audit/UI sau nay, qua adapter/query/, chua implement)
CREATE INDEX idx_scheduled_job_instance_scheduled_job_id ON scheduled_job_instance (scheduled_job_id);
