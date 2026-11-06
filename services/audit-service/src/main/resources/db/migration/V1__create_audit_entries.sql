-- Bảng bản ghi kiểm toán: chỉ ghi thêm (INV-AUD-01, ADR-0009).
-- Chạy bằng tài khoản chủ bảng (audit_owner). Tài khoản chạy dịch vụ (placeholder ${appRole}) chỉ được cấp INSERT và SELECT.

CREATE TABLE audit_entries (
    id               UUID         NOT NULL,
    source_event_id  VARCHAR(100) NOT NULL,
    occurred_at      TIMESTAMPTZ  NOT NULL,
    recorded_at      TIMESTAMPTZ  NOT NULL,
    actor_account_id UUID,
    actor_role       VARCHAR(32)  NOT NULL,
    action           VARCHAR(100) NOT NULL,
    target_type      VARCHAR(64)  NOT NULL,
    target_id        VARCHAR(100) NOT NULL,
    details          JSONB        NOT NULL DEFAULT '{}'::jsonb,
    source_service   VARCHAR(64)  NOT NULL,
    trace_id         VARCHAR(64),

    CONSTRAINT pk_audit_entries PRIMARY KEY (id),
    -- Một sự kiện nguồn chỉ cho một bản ghi: nhận lặp không ghi đôi (INV-AUD-02)
    CONSTRAINT uq_audit_entries_source_event_id UNIQUE (source_event_id),
    -- Người làm có mã tài khoản, trừ khi là hệ thống (INV-AUD-03, INV-AUD-05)
    CONSTRAINT ck_audit_entries_actor CHECK (actor_account_id IS NOT NULL OR actor_role = 'SYSTEM')
);

-- Bốn chỉ mục cho tra cứu theo con trỏ (occurred_at, id) giảm dần; lọc theo người làm, đối tượng, hành động
CREATE INDEX ix_audit_entries_occurred ON audit_entries (occurred_at DESC, id DESC);
CREATE INDEX ix_audit_entries_actor ON audit_entries (actor_account_id, occurred_at DESC, id DESC) WHERE actor_account_id IS NOT NULL;
CREATE INDEX ix_audit_entries_target ON audit_entries (target_type, target_id, occurred_at DESC, id DESC);
CREATE INDEX ix_audit_entries_action ON audit_entries (action, occurred_at DESC, id DESC);

-- Lớp chặn thứ hai (lớp thứ nhất là quyền): từ chối UPDATE, DELETE, TRUNCATE cho mọi tài khoản, kể cả chủ bảng.
-- Gỡ lớp này phải là lệnh ALTER TABLE tường minh của chủ bảng.
CREATE FUNCTION audit_entries_reject_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'audit_entries là bảng chỉ ghi thêm: % bị từ chối', TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TRIGGER trg_audit_entries_reject_update_delete
    BEFORE UPDATE OR DELETE ON audit_entries
    FOR EACH ROW EXECUTE FUNCTION audit_entries_reject_change();

CREATE TRIGGER trg_audit_entries_reject_truncate
    BEFORE TRUNCATE ON audit_entries
    FOR EACH STATEMENT EXECUTE FUNCTION audit_entries_reject_change();

-- Lớp chặn thứ nhất: tài khoản dịch vụ chỉ thêm và đọc
REVOKE ALL ON audit_entries FROM PUBLIC;
GRANT SELECT, INSERT ON audit_entries TO ${appRole};
