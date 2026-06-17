package vn.t3nexus.catalog.infrastructure.adapter.storage;

public class ObjectStorageException extends RuntimeException {
    public ObjectStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
