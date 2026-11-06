package vn.t3nexus.catalog.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Ai thay đổi, mã event và thời điểm: use case tạo một lần, truyền vào hành vi của aggregate để phát event. */
public record ChangeContext(CatalogActor actor, UUID eventId, Instant now) {

    public ChangeContext {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(now, "now");
    }
}
