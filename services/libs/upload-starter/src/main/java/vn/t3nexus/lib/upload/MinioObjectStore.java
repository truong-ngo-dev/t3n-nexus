package vn.t3nexus.lib.upload;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PostPolicy;
import io.minio.RemoveObjectArgs;
import io.minio.SetBucketPolicyArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Cài đặt {@link ObjectStore} bằng MinIO (S3 tương thích). Mọi lỗi hạ tầng thành {@link ObjectStoreException}. */
public class MinioObjectStore implements ObjectStore {

    private final MinioClient client;
    private final MinioConnectionProperties connection;

    public MinioObjectStore(MinioClient client, MinioConnectionProperties connection) {
        this.client = client;
        this.connection = connection;
    }

    @Override
    public PostForm presignPost(String bucket, String key, String contentType, long maxSize, Duration ttl) {
        try {
            PostPolicy policy = new PostPolicy(bucket, ZonedDateTime.now(ZoneOffset.UTC).plus(ttl));
            policy.addEqualsCondition("key", key);
            policy.addEqualsCondition("Content-Type", contentType);
            policy.addContentLengthRangeCondition(1, maxSize);
            // MinIO chỉ trả các trường chữ ký; khóa và loại là điều kiện của policy, client phải gửi đúng giá trị đó
            Map<String, String> fields = new LinkedHashMap<>(client.getPresignedPostFormData(policy));
            fields.put("key", key);
            fields.put("Content-Type", contentType);
            String endpoint = connection.formEndpoint();
            String base = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
            return new PostForm(base + "/" + bucket, fields);
        } catch (Exception e) {
            throw new ObjectStoreException("Không ký được form tải lên cho " + key, e);
        }
    }

    @Override
    public Optional<StoredObject> stat(String bucket, String key) {
        try {
            StatObjectResponse stat = client.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build());
            return Optional.of(new StoredObject(stat.size(), stat.contentType()));
        } catch (ErrorResponseException e) {
            String code = e.errorResponse().code();
            if ("NoSuchKey".equals(code) || "NoSuchObject".equals(code)) {
                return Optional.empty();
            }
            throw new ObjectStoreException("Không đọc được thông tin đối tượng " + key, e);
        } catch (Exception e) {
            throw new ObjectStoreException("Không đọc được thông tin đối tượng " + key, e);
        }
    }

    @Override
    public byte[] readHead(String bucket, String key, int length) {
        try (GetObjectResponse response = client.getObject(
                GetObjectArgs.builder().bucket(bucket).object(key).offset(0L).length((long) length).build())) {
            return response.readAllBytes();
        } catch (Exception e) {
            throw new ObjectStoreException("Không đọc được đầu tệp " + key, e);
        }
    }

    @Override
    public void delete(String bucket, String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            throw new ObjectStoreException("Không xóa được đối tượng " + key, e);
        }
    }

    @Override
    public void ensureBucket(String bucket, boolean publicRead) {
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            if (publicRead) {
                String policy = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\","
                        + "\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetObject\"],"
                        + "\"Resource\":[\"arn:aws:s3:::" + bucket + "/*\"]}]}";
                client.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucket).config(policy).build());
            }
        } catch (Exception e) {
            throw new ObjectStoreException("Không chuẩn bị được bucket " + bucket, e);
        }
    }
}
