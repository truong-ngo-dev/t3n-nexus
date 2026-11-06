package vn.t3nexus.lib.upload;

import io.minio.MinioClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Thư viện với MinIO thật (Testcontainers, không dùng MinIO dev): POST policy ép loại, dung lượng, khóa và hạn;
 * chữ ký tệp; đọc công khai; toàn bộ vòng đời chỗ với kho thật (KT-01, KT-02 phía hạ tầng; ADR-0013 mục "Cần kiểm").
 */
class MinioObjectStoreTest {

    private static final long MAX = 1024;
    private static final byte[] JPEG = jpeg(200);

    private static GenericContainer<?> minio;
    private static MinioObjectStore store;
    private static String publicBase;
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @BeforeAll
    static void start() {
        minio = new GenericContainer<>("quay.io/minio/minio:latest")
                .withCommand("server", "/data")
                .withEnv("MINIO_ROOT_USER", "minioadmin")
                .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
                .withExposedPorts(9000)
                .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));
        minio.start();
        String endpoint = "http://" + minio.getHost() + ":" + minio.getMappedPort(9000);
        MinioConnectionProperties connection = new MinioConnectionProperties();
        connection.setEndpoint(endpoint);
        store = new MinioObjectStore(MinioClient.builder().endpoint(endpoint)
                .credentials("minioadmin", "minioadmin").build(), connection);
        store.ensureBucket("pub", true);
        store.ensureBucket("priv", false);
        publicBase = endpoint;
    }

    @AfterAll
    static void stop() {
        minio.stop();
    }

    @Test
    void validUploadIsAcceptedAndReadable() throws Exception {
        String key = "k/" + UUID.randomUUID();
        ObjectStore.PostForm form = store.presignPost("pub", key, "image/jpeg", MAX, Duration.ofMinutes(5));

        assertThat(post(form, form.fields(), JPEG)).isEqualTo(204);

        assertThat(store.stat("pub", key)).hasValueSatisfying(o -> assertThat(o.size()).isEqualTo(JPEG.length));
        assertThat(store.readHead("pub", key, 3)).containsExactly((byte) 0xFF, (byte) 0xD8, (byte) 0xFF);
    }

    @Test
    void wrongContentTypeIsRejectedByStore() throws Exception {
        String key = "k/" + UUID.randomUUID();
        ObjectStore.PostForm form = store.presignPost("pub", key, "image/jpeg", MAX, Duration.ofMinutes(5));
        Map<String, String> fields = new LinkedHashMap<>(form.fields());
        fields.put("Content-Type", "application/x-msdownload");

        assertThat(post(form, fields, JPEG)).isGreaterThanOrEqualTo(400);
        assertThat(store.stat("pub", key)).isEmpty();
    }

    @Test
    void oversizedFileIsRejectedByStore() throws Exception {
        String key = "k/" + UUID.randomUUID();
        ObjectStore.PostForm form = store.presignPost("pub", key, "image/jpeg", MAX, Duration.ofMinutes(5));

        assertThat(post(form, form.fields(), jpeg((int) MAX + 1))).isGreaterThanOrEqualTo(400);
        assertThat(store.stat("pub", key)).isEmpty();
    }

    @Test
    void tamperedKeyIsRejectedByStore() throws Exception {
        String key = "k/" + UUID.randomUUID();
        String other = "k/" + UUID.randomUUID();
        ObjectStore.PostForm form = store.presignPost("pub", key, "image/jpeg", MAX, Duration.ofMinutes(5));
        Map<String, String> fields = new LinkedHashMap<>(form.fields());
        fields.put("key", other);

        assertThat(post(form, fields, JPEG)).isGreaterThanOrEqualTo(400);
        assertThat(store.stat("pub", other)).isEmpty();
        assertThat(store.stat("pub", key)).isEmpty();
    }

    @Test
    void expiredPolicyIsRejectedByStore() throws Exception {
        String key = "k/" + UUID.randomUUID();
        ObjectStore.PostForm form = store.presignPost("pub", key, "image/jpeg", MAX, Duration.ofSeconds(1));
        Thread.sleep(2500);

        assertThat(post(form, form.fields(), JPEG)).isGreaterThanOrEqualTo(400);
        assertThat(store.stat("pub", key)).isEmpty();
    }

    @Test
    void missingObjectAndIdempotentDelete() {
        assertThat(store.stat("pub", "k/khong-co")).isEmpty();
        store.delete("pub", "k/khong-co");
    }

    @Test
    void publicBucketIsReadableAnonymouslyAndPrivateIsNot() throws Exception {
        String key = "k/" + UUID.randomUUID();
        ObjectStore.PostForm pub = store.presignPost("pub", key, "image/jpeg", MAX, Duration.ofMinutes(5));
        ObjectStore.PostForm priv = store.presignPost("priv", key, "image/jpeg", MAX, Duration.ofMinutes(5));
        assertThat(post(pub, pub.fields(), JPEG)).isEqualTo(204);
        assertThat(post(priv, priv.fields(), JPEG)).isEqualTo(204);

        assertThat(get(publicBase + "/pub/" + key)).isEqualTo(200);
        assertThat(get(publicBase + "/priv/" + key)).isEqualTo(403);
    }

    @Test
    void fullLifecycleAgainstRealStore() throws Exception {
        UploadProperties props = new UploadProperties();
        UploadProperties.Purpose purpose = new UploadProperties.Purpose();
        purpose.setBucket("pub");
        purpose.setKeyPrefix("products");
        purpose.setPublicRead(true);
        purpose.setPublicBaseUrl(publicBase);
        purpose.setMaxSize(MAX);
        props.getPurposes().put("product-image", purpose);
        UploadSlots slots = new UploadSlots(props, store, new InMemoryUploadSlotStore(), UUID::randomUUID,
                Clock.systemUTC());
        UUID account = UUID.randomUUID();
        UUID product = UUID.randomUUID();

        // 1. Ảnh thật: xin chỗ -> tải -> kiểm -> nhận -> đọc công khai
        UploadSlots.Issued issued = slots.issue(
                new UploadSlots.IssueRequest("product-image", account, product, "image/jpeg", JPEG.length));
        assertThat(issued.objectKey()).isEqualTo("products/" + product + "/" + issued.slotId());
        assertThat(post(new ObjectStore.PostForm(issued.url(), issued.fields()), issued.fields(), JPEG)).isEqualTo(204);
        UploadSlots.Verified verified = slots.verify(issued.slotId(), "product-image", account, product);
        assertThat(verified.size()).isEqualTo(JPEG.length);
        UploadSlots.Claimed claimed = slots.consume(verified);
        assertThat(get(slots.urlOf("product-image", claimed.objectKey()))).isEqualTo(200);

        // 2. Tệp chữ khai là JPEG nhưng nội dung không phải ảnh: policy cho qua (chỉ ép tiêu đề), verify phải bắt
        UploadSlots.Issued fake = slots.issue(
                new UploadSlots.IssueRequest("product-image", account, product, "image/jpeg", 100));
        byte[] text = "đây không phải ảnh, chỉ là văn bản giả đuôi jpg".getBytes(StandardCharsets.UTF_8);
        assertThat(post(new ObjectStore.PostForm(fake.url(), fake.fields()), fake.fields(), text)).isEqualTo(204);
        assertThatThrownBy(() -> slots.verify(fake.slotId(), "product-image", account, product))
                .isInstanceOf(DomainException.class).hasMessageContaining("Nội dung tệp");
        assertThat(store.stat("pub", fake.objectKey())).isEmpty();

        // 3. Chưa tải lên thì chưa nhận được
        UploadSlots.Issued notUploaded = slots.issue(
                new UploadSlots.IssueRequest("product-image", account, product, "image/png", 100));
        assertThatThrownBy(() -> slots.verify(notUploaded.slotId(), "product-image", account, product))
                .isInstanceOf(DomainException.class).hasMessageContaining("chưa được tải lên");
    }

    // ───────────── Trợ giúp ─────────────

    /** Gửi form multipart tới kho như trình duyệt: các trường trước, tệp (tên trường "file") cuối cùng. */
    private static int post(ObjectStore.PostForm form, Map<String, String> fields, byte[] content) throws Exception {
        String boundary = "----boundary" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (Map.Entry<String, String> field : fields.entrySet()) {
            body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + field.getKey()
                    + "\"\r\n\r\n" + field.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"f\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.writeBytes(content);
        body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(form.url()))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
        return HTTP.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private static int get(String url) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private static byte[] jpeg(int size) {
        byte[] bytes = new byte[size];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xD8;
        bytes[2] = (byte) 0xFF;
        return bytes;
    }
}
