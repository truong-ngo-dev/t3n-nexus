package vn.t3nexus.lib.upload;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FakeObjectStore implements ObjectStore {

    public boolean failing = false;
    public final List<String> deleted = new ArrayList<>();
    public final List<String> ensuredBuckets = new ArrayList<>();
    public PresignCall lastPresign;

    private final Map<String, StoredData> storage = new HashMap<>();

    public record PresignCall(String bucket, String key, String contentType, long maxSize, Duration ttl) {
    }

    private record StoredData(byte[] content, String contentType) {
    }

    public void put(String bucket, String key, byte[] content, String contentType) {
        storage.put(bucket + "/" + key, new StoredData(content, contentType));
    }

    @Override
    public PostForm presignPost(String bucket, String key, String contentType, long maxSize, Duration ttl) {
        if (failing) {
            throw new ObjectStoreException("Giả lập lỗi store khi presignPost", null);
        }
        this.lastPresign = new PresignCall(bucket, key, contentType, maxSize, ttl);
        return new PostForm("http://store/" + bucket, Map.of("key", key, "Content-Type", contentType));
    }

    @Override
    public Optional<StoredObject> stat(String bucket, String key) {
        if (failing) {
            throw new ObjectStoreException("Giả lập lỗi store khi stat", null);
        }
        StoredData data = storage.get(bucket + "/" + key);
        if (data == null) {
            return Optional.empty();
        }
        return Optional.of(new StoredObject(data.content().length, data.contentType()));
    }

    @Override
    public byte[] readHead(String bucket, String key, int length) {
        if (failing) {
            throw new ObjectStoreException("Giả lập lỗi store khi readHead", null);
        }
        StoredData data = storage.get(bucket + "/" + key);
        if (data == null) {
            return new byte[0];
        }
        int actualLen = Math.min(length, data.content().length);
        return Arrays.copyOf(data.content(), actualLen);
    }

    @Override
    public void delete(String bucket, String key) {
        if (failing) {
            throw new ObjectStoreException("Giả lập lỗi store khi delete", null);
        }
        deleted.add(bucket + "/" + key);
        storage.remove(bucket + "/" + key);
    }

    @Override
    public void ensureBucket(String bucket, boolean publicRead) {
        if (failing) {
            throw new ObjectStoreException("Giả lập lỗi store khi ensureBucket", null);
        }
        ensuredBuckets.add(bucket + ":" + publicRead);
    }
}
