package vn.t3nexus.lib.upload;

/** Kho đối tượng không phản hồi hoặc lỗi hạ tầng. */
public class ObjectStoreException extends RuntimeException {

    public ObjectStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
