-- Mẫu migration (Postgres) cho bảng hộp thư event. Mọi BC phát event dùng cùng một hình bảng (cùng tên và kiểu cột):
-- chép vào migration của BC (Flyway), không sửa tên hay kiểu cột. Entity OutboxEvent và cấu hình connector Debezium
-- (EventRouter) đọc đúng các cột này.
--
--   event_id    mã event (UUID v7, ADR-0001), duy nhất: một event không vào hộp thư hai lần; là khóa khử trùng ở bên nhận
--   aggregate_id thành khóa thông điệp (cùng thực thể, cùng chủ đề thì đúng thứ tự)
--   routing_key chủ đề đích
--   payload     khung event (JSON dạng văn bản)

CREATE TABLE outbox_events (
    id             BIGINT GENERATED ALWAYS AS IDENTITY,
    event_id       VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   VARCHAR(100) NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    routing_key    VARCHAR(255) NOT NULL,
    payload        TEXT         NOT NULL,
    occurred_on    TIMESTAMPTZ  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    trace_id       VARCHAR(64),
    span_id        VARCHAR(64),

    CONSTRAINT pk_outbox_events PRIMARY KEY (id),
    CONSTRAINT uq_outbox_events_event_id UNIQUE (event_id)
);

CREATE INDEX idx_outbox_events_created_at ON outbox_events (created_at);
