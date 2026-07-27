package vn.t3nexus.lib.eventsourcing;

/**
 * Thrown when a domain event cannot be serialized for storage, or a stored payload
 * cannot be deserialized back into its original event type on replay.
 */
public class EventStoreSerializationException extends RuntimeException {

    public EventStoreSerializationException(String eventType, Throwable cause) {
        super("Failed to (de)serialize event: " + eventType, cause);
    }
}
