-- ============================================================
-- scheduler-service — V2: scheduled_job
-- ============================================================

-- ------------------------------------------------------------
-- scheduled_job
-- ScheduleStore — nguon su that. PENDING <=> next_fire_at IS NULL (bat bien, xem service.md).
-- id la ULID (26 ky tu), khong phai UUID — sinh o Application Handler qua ULIDGenerator, pre-compute.
-- ------------------------------------------------------------
CREATE TABLE scheduled_job (
    id                   VARCHAR(26)     NOT NULL,
    task_type            VARCHAR(100)    NOT NULL,
    schedule_type        VARCHAR(20)     NOT NULL,
    cron_expression      VARCHAR(100),
    timezone             VARCHAR(50),
    due_at               TIMESTAMPTZ,
    payload              JSONB           NOT NULL DEFAULT '{}',
    status               VARCHAR(20)     NOT NULL,
    next_fire_at         TIMESTAMPTZ,
    misfire_instruction  VARCHAR(20)     NOT NULL DEFAULT 'FIRE_NOW',
    version              BIGINT          NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ     NOT NULL,
    updated_at           TIMESTAMPTZ     NOT NULL,

    CONSTRAINT pk_scheduled_job PRIMARY KEY (id),
    CONSTRAINT ck_scheduled_job_due_at
        CHECK (schedule_type <> 'ONE_OFF' OR due_at IS NOT NULL),
    CONSTRAINT ck_scheduled_job_cron_expression
        CHECK (schedule_type <> 'RECURRING' OR cron_expression IS NOT NULL),
    CONSTRAINT ck_scheduled_job_status
        CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED')),
    CONSTRAINT ck_scheduled_job_next_fire_at
        CHECK ((status IN ('PENDING', 'COMPLETED')) = (next_fire_at IS NULL)),
    CONSTRAINT ck_scheduled_job_misfire_instruction
        CHECK (misfire_instruction IN ('FIRE_NOW', 'DO_NOTHING'))
);

-- Partial index — chỉ RUNNING (đang được poll) mới cần tra next_fire_at, tập tự nhỏ/tự rỗng dần
CREATE INDEX idx_scheduled_job_next_fire ON scheduled_job (next_fire_at) WHERE status = 'RUNNING';
CREATE INDEX idx_scheduled_job_task_type ON scheduled_job (task_type);
