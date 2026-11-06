package vn.t3nexus.lib.upload;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kết nối MinIO, cùng tên cấu hình với {@code identity-service} ({@code app.minio.*}).
 * {@code publicEndpoint} là địa chỉ mà <b>trình duyệt</b> gọi để tải lên; để trống thì dùng {@code endpoint}.
 */
@ConfigurationProperties(prefix = "app.minio")
public class MinioConnectionProperties {

    private String endpoint = "http://localhost:9000";
    private String publicEndpoint;
    private String accessKey = "minioadmin";
    private String secretKey = "minioadmin";

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getPublicEndpoint() { return publicEndpoint; }
    public void setPublicEndpoint(String publicEndpoint) { this.publicEndpoint = publicEndpoint; }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }

    String formEndpoint() {
        return publicEndpoint == null || publicEndpoint.isBlank() ? endpoint : publicEndpoint;
    }
}
