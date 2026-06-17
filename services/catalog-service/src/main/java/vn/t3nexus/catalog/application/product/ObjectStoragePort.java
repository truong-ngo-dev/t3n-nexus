package vn.t3nexus.catalog.application.product;

public interface ObjectStoragePort {

    String generatePresignedPutUrl(String objectKey, int ttlSeconds);

    boolean objectExists(String objectKey);
}
