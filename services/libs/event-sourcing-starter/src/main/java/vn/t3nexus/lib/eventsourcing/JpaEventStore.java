package vn.t3nexus.lib.eventsourcing;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.lib.common.domain.model.DomainEvent;
import vn.t3nexus.lib.common.domain.service.EventStore;
import vn.t3nexus.lib.common.domain.service.EventStoreConflictException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class JpaEventStore implements EventStore {

    private final EventStoreJpaRepository repository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<DomainEvent> loadEvents(String aggregateId) {
        return repository.findByAggregateIdOrderByRevisionAsc(aggregateId).stream()
                .map(this::deserialize)
                .toList();
    }

    @Override
    @Transactional
    public void append(String aggregateId, String aggregateType, List<DomainEvent> events, long expectedRevision) {
        if (events.isEmpty()) return;

        // One correlationId per append() call — groups every event that resulted from
        // the same use-case execution, regardless of how many events were raised.
        String correlationId = UUID.randomUUID().toString();

        List<EventStoreEntity> rows = new ArrayList<>(events.size());
        long revision = expectedRevision;
        for (DomainEvent event : events) {
            revision++;
            rows.add(toEntity(aggregateId, aggregateType, revision, correlationId, event));
        }

        try {
            // saveAllAndFlush forces the INSERTs (and the unique constraint check) to run
            // inside this method, so a conflict surfaces here instead of at commit time.
            repository.saveAllAndFlush(rows);
        } catch (DataIntegrityViolationException e) {
            throw new EventStoreConflictException(aggregateId, expectedRevision);
        }
    }

    private EventStoreEntity toEntity(String aggregateId, String aggregateType, long revision,
                                      String correlationId, DomainEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            return EventStoreEntity.create(
                    aggregateId, aggregateType, revision, correlationId,
                    event.getEventId(), event.getClass().getName(), payload, event.getOccurredOn());
        } catch (JacksonException e) {
            throw new EventStoreSerializationException(event.getClass().getName(), e);
        }
    }

    private DomainEvent deserialize(EventStoreEntity entity) {
        try {
            Class<?> eventClass = Class.forName(entity.getEventType());
            return (DomainEvent) objectMapper.readValue(entity.getPayload(), eventClass);
        } catch (ClassNotFoundException | JacksonException e) {
            throw new EventStoreSerializationException(entity.getEventType(), e);
        }
    }
}
