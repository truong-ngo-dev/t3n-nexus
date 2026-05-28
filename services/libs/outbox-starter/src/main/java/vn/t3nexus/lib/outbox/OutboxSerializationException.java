package vn.t3nexus.lib.outbox;

/**
 * Thrown when a domain event cannot be serialized to JSON for storage in the outbox.
 */
public class OutboxSerializationException extends RuntimeException {

    public OutboxSerializationException(String eventType, Throwable cause) {
        super("Failed to serialize domain event: " + eventType, cause);
    }
}
