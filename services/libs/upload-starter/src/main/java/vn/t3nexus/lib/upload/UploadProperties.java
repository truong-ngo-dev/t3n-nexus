package vn.t3nexus.lib.upload;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cấu hình tải lên tệp cho các mục đích sử dụng khác nhau.
 */
@ConfigurationProperties(prefix = "upload")
public class UploadProperties {

    private Map<String, Purpose> purposes = new HashMap<>();
    private Duration expiredGrace = Duration.ofHours(1);
    private Duration cleanInterval = Duration.ofMinutes(10);
    private int cleanBatch = 100;

    public Map<String, Purpose> getPurposes() {
        return purposes;
    }

    public void setPurposes(Map<String, Purpose> purposes) {
        this.purposes = purposes;
    }

    public Duration getExpiredGrace() {
        return expiredGrace;
    }

    public void setExpiredGrace(Duration expiredGrace) {
        this.expiredGrace = expiredGrace;
    }

    public Duration getCleanInterval() {
        return cleanInterval;
    }

    public void setCleanInterval(Duration cleanInterval) {
        this.cleanInterval = cleanInterval;
    }

    public int getCleanBatch() {
        return cleanBatch;
    }

    public void setCleanBatch(int cleanBatch) {
        this.cleanBatch = cleanBatch;
    }

    public static class Purpose {
        private String bucket;
        private String keyPrefix;
        private boolean publicRead;
        private String publicBaseUrl;
        private List<String> allowedTypes = List.of("image/jpeg", "image/png", "image/webp");
        private long maxSize = 5L * 1024 * 1024;
        private Duration ttl = Duration.ofMinutes(5);
        private int maxPending = 9;

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }

        public boolean isPublicRead() {
            return publicRead;
        }

        public void setPublicRead(boolean publicRead) {
            this.publicRead = publicRead;
        }

        public String getPublicBaseUrl() {
            return publicBaseUrl;
        }

        public void setPublicBaseUrl(String publicBaseUrl) {
            this.publicBaseUrl = publicBaseUrl;
        }

        public List<String> getAllowedTypes() {
            return allowedTypes;
        }

        public void setAllowedTypes(List<String> allowedTypes) {
            this.allowedTypes = allowedTypes;
        }

        public long getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(long maxSize) {
            this.maxSize = maxSize;
        }

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }

        public int getMaxPending() {
            return maxPending;
        }

        public void setMaxPending(int maxPending) {
            this.maxPending = maxPending;
        }
    }
}
