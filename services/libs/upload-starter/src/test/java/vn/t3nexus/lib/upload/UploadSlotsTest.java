package vn.t3nexus.lib.upload;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.service.IdGenerator;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadSlotsTest {

    private static class MutableClock extends Clock {
        private final AtomicReference<Instant> currentInstant;
        private final ZoneId zone;

        MutableClock(Instant initial, ZoneId zone) {
            this.currentInstant = new AtomicReference<>(initial);
            this.zone = zone;
        }

        public void set(Instant newInstant) {
            currentInstant.set(newInstant);
        }

        public void advance(Duration duration) {
            currentInstant.updateAndGet(i -> i.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(currentInstant.get(), zone);
        }

        @Override
        public Instant instant() {
            return currentInstant.get();
        }
    }

    private static class FakeIdGenerator implements IdGenerator {
        private final AtomicLong counter = new AtomicLong(1);

        @Override
        public UUID generate() {
            return new UUID(0L, counter.getAndIncrement());
        }
    }

    private UploadProperties props;
    private FakeObjectStore store;
    private InMemoryUploadSlotStore slotStore;
    private FakeIdGenerator idGenerator;
    private MutableClock clock;
    private UploadSlots uploadSlots;

    private final Instant baseTime = Instant.parse("2026-01-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        props = new UploadProperties();
        var purpose = new UploadProperties.Purpose();
        purpose.setBucket("products");
        purpose.setKeyPrefix("products");
        purpose.setPublicRead(true);
        purpose.setPublicBaseUrl("http://cdn/");
        purpose.setMaxSize(5L * 1024 * 1024);
        purpose.setTtl(Duration.ofMinutes(5));
        purpose.setMaxPending(3);
        purpose.setAllowedTypes(List.of("image/jpeg", "image/png", "image/webp"));
        props.setPurposes(Map.of("product-image", purpose));
        props.setExpiredGrace(Duration.ofHours(1));
        props.setCleanBatch(100);

        store = new FakeObjectStore();
        slotStore = new InMemoryUploadSlotStore();
        idGenerator = new FakeIdGenerator();
        clock = new MutableClock(baseTime, ZoneOffset.UTC);

        uploadSlots = new UploadSlots(props, store, slotStore, idGenerator, clock);
    }

    // --- issue tests ---

    @Test
    void issue_thanhCong() {
        UUID accountId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        var issued = uploadSlots.issue(new UploadSlots.IssueRequest(
                "product-image", accountId, targetId, "image/jpeg", 1024
        ));

        UUID expectedSlotId = new UUID(0L, 1L);
        String expectedKey = "products/" + targetId + "/" + expectedSlotId;
        Instant expectedExpires = baseTime.plus(Duration.ofMinutes(5));

        assertThat(issued.slotId()).isEqualTo(expectedSlotId);
        assertThat(issued.objectKey()).isEqualTo(expectedKey);
        assertThat(issued.expiresAt()).isEqualTo(expectedExpires);
        assertThat(issued.maxSize()).isEqualTo(5L * 1024 * 1024);
        assertThat(issued.url()).isEqualTo("http://store/products");
        assertThat(issued.fields()).isEqualTo(Map.of("key", expectedKey, "Content-Type", "image/jpeg"));

        // Chỗ được lưu
        var savedSlot = slotStore.findById(expectedSlotId).orElseThrow();
        assertThat(savedSlot.purpose()).isEqualTo("product-image");
        assertThat(savedSlot.accountId()).isEqualTo(accountId);
        assertThat(savedSlot.targetId()).isEqualTo(targetId);
        assertThat(savedSlot.declaredSize()).isEqualTo(1024);

        // Tham số presign dùng maxSize của mục đích
        assertThat(store.lastPresign).isNotNull();
        assertThat(store.lastPresign.maxSize()).isEqualTo(5L * 1024 * 1024);
    }

    @Test
    void issue_loaiKhongChoPhep() {
        UUID accountId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        assertThatThrownBy(() -> uploadSlots.issue(new UploadSlots.IssueRequest(
                "product-image", accountId, targetId, "application/pdf", 1024
        )))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).getErrorCode())
                        .isEqualTo(UploadErrorCode.UPLOAD_TYPE_NOT_ALLOWED));
    }

    @Test
    void issue_size0() {
        UUID accountId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        assertThatThrownBy(() -> uploadSlots.issue(new UploadSlots.IssueRequest(
                "product-image", accountId, targetId, "image/jpeg", 0
        )))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).getErrorCode())
                        .isEqualTo(UploadErrorCode.UPLOAD_SIZE_INVALID));
    }

    @Test
    void issue_sizeAm() {
        UUID accountId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        assertThatThrownBy(() -> uploadSlots.issue(new UploadSlots.IssueRequest(
                "product-image", accountId, targetId, "image/jpeg", -10
        )))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).getErrorCode())
                        .isEqualTo(UploadErrorCode.UPLOAD_SIZE_INVALID));
    }

    @Test
    void issue_sizeQuaCo() {
        UUID accountId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        assertThatThrownBy(() -> uploadSlots.issue(new UploadSlots.IssueRequest(
                "product-image", accountId, targetId, "image/jpeg", 5L * 1024 * 1024 + 1
        )))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).getErrorCode())
                        .isEqualTo(UploadErrorCode.UPLOAD_SIZE_INVALID));
    }

    @Test
    void issue_duMaxPending_caThu4BaoPendingLimit_nhungHetHanKhongTinh() {
        UUID accountId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        // Cấp 3 chỗ (maxPending = 3)
        uploadSlots.issue(new UploadSlots.IssueRequest("product-image", accountId, targetId, "image/jpeg", 100));
        uploadSlots.issue(new UploadSlots.IssueRequest("product-image", accountId, targetId, "image/jpeg", 100));
        uploadSlots.issue(new UploadSlots.IssueRequest("product-image", accountId, targetId, "image/jpeg", 100));

        // Ca thứ 4 bị giới hạn
        assertThatThrownBy(() -> uploadSlots.issue(new UploadSlots.IssueRequest(
                "product-image", accountId, targetId, "image/jpeg", 100
        )))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).getErrorCode())
                        .isEqualTo(UploadErrorCode.UPLOAD_PENDING_LIMIT));

        // Tiến thời gian qua TTL (5 phút) để các chỗ cũ hết hạn
        clock.advance(Duration.ofMinutes(6));

        // Giờ có thể cấp được vì các chỗ cũ đã hết hạn
        var issued = uploadSlots.issue(new UploadSlots.IssueRequest(
                "product-image", accountId, targetId, "image/jpeg", 100
        ));
        assertThat(issued).isNotNull();
    }

    @Test
    void issue_storeLoi_baoUnavailable_vaKhongLuuCho() {
        UUID accountId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        store.failing = true;

        assertThatThrownBy(() -> uploadSlots.issue(new UploadSlots.IssueRequest(
                "product-image", accountId, targetId, "image/jpeg", 100
        )))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).getErrorCode())
                        .isEqualTo(UploadErrorCode.UPLOAD_STORAGE_UNAVAILABLE));

        // Không lưu chỗ nào
        assertThat(slotStore.findById(new UUID(0L, 1L))).isEmpty();
    }

    @Test
    void issue_mucDichLa_nemIllegalArgumentException() {
        UUID accountId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        assertThatThrownBy(() -> uploadSlots.issue(new UploadSlots.IssueRequest(
                "unknown-purpose", accountId, targetId, "image/jpeg", 100
        )))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- verify tests ---

    @Test
    void verify_thanhCongVoiJpegThat() {
        UUID accountId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        var issued = uploadSlots.issue(new UploadSlots.IssueRequest(
                "product-image", accountId, targetId, "image/jpeg", 1000
        ));

        // Giả lập người dùng tải lên đối tượng JPEG thật (FF D8 FF + đệm)
        byte[] content = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01, 0x02, 0x03, 0x04};
        store.put("products", issued.objectKey(), content, "image/jpeg");

        var verified = uploadSlots.verify(issued.slotId(), "product-image", accountId, targetId);
        assertThat(verified.slotId()).isEqualTo(issued.slotId());
        assertThat(verified.purpose()).isEqualTo("product-image");
        assertThat(verified.objectKey()).isEqualTo(issued.objectKey());
        assertThat(verified.contentType()).isEqualTo("image/jpeg");
        assertThat(verified.size()).isEqualTo(content.length);

        // Chỗ vẫn còn trong slotStore (chưa consume)
        assertThat(slotStore.findById(issued.slotId())).isPresent();
    }

    @Test
    void verify_choKhongCo() {
        UUID accountId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        assertThatThrownBy(() -> uploadSlots.verify(UUID.randomUUID(), "product-image", accountId, targetId))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).getErrorCode())
                        .isEqualTo(UploadErrorCode.UPLOAD_SLOT_NOT_FOUND));
    }

    @Test
    void verify_choCuaTaiKhoanKhac() {
        UUID accountId = UUID.randomUUID();
        UUID otherAccount = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        var issued = uploadSlots.issue(new UploadSlots.IssueRequest(
                "product-image", accountId, targetId, "image/jpeg", 1000
        ));

        assertThatThrownBy(() -> uploadSlots.verify(issued.slotId(), "product-image", otherAccount, targetId))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).getErrorCode())
                        .isEqualTo(UploadErrorCode.UPLOAD_SLOT_NOT_FOUND));
    }

    @Test
    void verify_choCuaDoiTuongKhac() {
        UUID accountId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID otherTarget = UUID.randomUUID();

        var issued = uploadSlots.issue(new UploadSlots.IssueRequest(
                "product-image", accountId, targetId, "image/jpeg", 1000
        ));

        assertThatThrownBy(() -> uploadSlots.verify(issued.slotId(), "product-image", accountId, otherTarget))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).getErrorCode())
                        .isEqualTo(UploadErrorCode.UPLOAD_SLOT_NOT_FOUND));
    }

    @Test
    void verify_choCuaMucDichKhac() {
        UUID accountId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        var issued = uploadSlots.issue(new UploadSlots.IssueRequest(
                "product-image", accountId, targetId, "image/jpeg", 1000
        ));

        assertThatThrownBy(() -> uploadSlots.verify(issued.slotId(), "other-purpose", accountId, targetId))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).getErrorCode())
                        .isEqualTo(UploadErrorCode.UPLOAD_SLOT_NOT_FOUND));
    }

    @Test
    void verify_hetHanDungTaiThoiDiemExpiresAt() {
        UUID accountId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        var issued = uploadSlots.issue(new UploadSlots.IssueRequest(
                "product-image", accountId, targetId, "image/jpeg", 1000
        ));

        // Nhảy đến đúng lúc expiresAt (now == expiresAt)
        clock.set(issued.expiresAt());

        assertThatThrownBy(() -> uploadSlots.verify(issued.slotId(), "product-image", accountId, targetId))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).getErrorCode())
                        .isEqualTo(UploadErrorCode.UPLOAD_SLOT_EXPIRED));
    }

    @Test
    void verify_chuaTai_baoNotCompleted_vaChoConNguyen() {
        UUID accountId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        var issued = uploadSlots.issue(new UploadSlots.IssueRequest(
                "product-image", accountId, targetId, "image/jpeg", 1000
        ));

        // Chưa put gì lên store
        assertThatThrownBy(() -> uploadSlots.verify(issued.slotId(), "product-image", accountId, targetId))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).getErrorCode())
                        .isEqualTo(UploadErrorCode.UPLOAD_NOT_COMPLETED));

        assertThat(slotStore.findById(issued.slotId())).isPresent();
    }

    @Test
    void verify_tepVanBanDuoiAnh_baoContentInvalid_doiTuongBiXoa_choBiXoa() {
        UUID accountId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        var issued = uploadSlots.issue(new UploadSlots.IssueRequest(
                "product-image", accountId, targetId, "image/jpeg", 1000
        ));

        byte[] textContent = "this is plain text".getBytes(StandardCharsets.UTF_8);
        store.put("products", issued.objectKey(), textContent, "image/jpeg");

        assertThatThrownBy(() -> uploadSlots.verify(issued.slotId(), "product-image", accountId, targetId))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).getErrorCode())
                        .isEqualTo(UploadErrorCode.UPLOAD_CONTENT_INVALID));

        assertThat(store.deleted).contains("products/" + issued.objectKey());
        assertThat(slotStore.findById(issued.slotId())).isEmpty();
    }

    @Test
    void verify_khaiPngNhungNoiDungJpeg_baoContentInvalid() {
        UUID accountId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        var issued = uploadSlots.issue(new UploadSlots.IssueRequest(
                "product-image", accountId, targetId, "image/png", 1000
        ));

        byte[] jpegContent = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01};
        store.put("products", issued.objectKey(), jpegContent, "image/png");

        assertThatThrownBy(() -> uploadSlots.verify(issued.slotId(), "product-image", accountId, targetId))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).getErrorCode())
                        .isEqualTo(UploadErrorCode.UPLOAD_CONTENT_INVALID));

        assertThat(store.deleted).contains("products/" + issued.objectKey());
        assertThat(slotStore.findById(issued.slotId())).isEmpty();
    }

    @Test
    void verify_dungLuongThatVuotMaxSize_baoContentInvalid_vaDoiTuongBiXoa() {
        UUID accountId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        var issued = uploadSlots.issue(new UploadSlots.IssueRequest(
                "product-image", accountId, targetId, "image/jpeg", 1000
        ));

        // Tạo nội dung lớn hơn maxSize (5 MiB + 10 bytes)
        byte[] bigContent = new byte[(int) (5L * 1024 * 1024 + 10)];
        // Chữ ký JPEG hợp lệ để chỉ có dung lượng là lý do bị từ chối
        bigContent[0] = (byte) 0xFF;
        bigContent[1] = (byte) 0xD8;
        bigContent[2] = (byte) 0xFF;
        store.put("products", issued.objectKey(), bigContent, "image/jpeg");

        assertThatThrownBy(() -> uploadSlots.verify(issued.slotId(), "product-image", accountId, targetId))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).getErrorCode())
                        .isEqualTo(UploadErrorCode.UPLOAD_CONTENT_INVALID));

        assertThat(store.deleted).contains("products/" + issued.objectKey());
        assertThat(slotStore.findById(issued.slotId())).isEmpty();
    }

    @Test
    void verify_storeLoi_baoUnavailable_vaChoCon() {
        UUID accountId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        var issued = uploadSlots.issue(new UploadSlots.IssueRequest(
                "product-image", accountId, targetId, "image/jpeg", 1000
        ));

        store.failing = true;

        assertThatThrownBy(() -> uploadSlots.verify(issued.slotId(), "product-image", accountId, targetId))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).getErrorCode())
                        .isEqualTo(UploadErrorCode.UPLOAD_STORAGE_UNAVAILABLE));

        assertThat(slotStore.findById(issued.slotId())).isPresent();
    }

    // --- consume tests ---

    @Test
    void consume_lanDauThanhCong_lanHaiBaoSlotNotFound() {
        UUID slotId = UUID.randomUUID();
        UploadSlot slot = new UploadSlot(
                slotId, "product-image", UUID.randomUUID(), UUID.randomUUID(),
                "products/obj1/key1", "image/jpeg", 100, baseTime.plus(Duration.ofMinutes(5)), baseTime
        );
        slotStore.save(slot);

        var verified = new UploadSlots.Verified(slotId, "product-image", "products/obj1/key1", "image/jpeg", 100);

        var claimed = uploadSlots.consume(verified);
        assertThat(claimed.objectKey()).isEqualTo("products/obj1/key1");
        assertThat(claimed.contentType()).isEqualTo("image/jpeg");
        assertThat(claimed.size()).isEqualTo(100);
        assertThat(slotStore.findById(slotId)).isEmpty();

        // Lần 2 với cùng Verified
        assertThatThrownBy(() -> uploadSlots.consume(verified))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).getErrorCode())
                        .isEqualTo(UploadErrorCode.UPLOAD_SLOT_NOT_FOUND));
    }

    // --- discard tests ---

    @Test
    void discard_xoaDoiTuong() {
        uploadSlots.discard("product-image", "products/target/slot1");
        assertThat(store.deleted).contains("products/products/target/slot1");
    }

    @Test
    void discard_storeLoiThiKhongNem() {
        store.failing = true;
        // Không ném exception
        uploadSlots.discard("product-image", "products/target/slot1");
    }

    // --- urlOf tests ---

    @Test
    void urlOf_ghepDungUrl() {
        String url = uploadSlots.urlOf("product-image", "target/slot1");
        assertThat(url).isEqualTo("http://cdn/products/target/slot1");
    }

    @Test
    void urlOf_mucDichKhongPublicRead_nemIllegalStateException() {
        props.getPurposes().get("product-image").setPublicRead(false);

        assertThatThrownBy(() -> uploadSlots.urlOf("product-image", "target/slot1"))
                .isInstanceOf(IllegalStateException.class);
    }

    // --- cleanExpired tests ---

    @Test
    void cleanExpired_cacTruongHop() {
        // expiredGrace = 1h
        Instant now = baseTime;
        clock.set(now);

        UUID idPastGrace = UUID.randomUUID(); // hết hạn trước now - 1h -> BỊ DỌN
        UUID idRecentExpired = UUID.randomUUID(); // hết hạn 30m trước (trong grace) -> GIỮ
        UUID idActive = UUID.randomUUID(); // còn hạn -> GIỮ

        UploadSlot slotPastGrace = new UploadSlot(
                idPastGrace, "product-image", UUID.randomUUID(), UUID.randomUUID(),
                "key-past-grace", "image/jpeg", 100,
                now.minus(Duration.ofHours(2)), now.minus(Duration.ofHours(3))
        );
        UploadSlot slotRecentExpired = new UploadSlot(
                idRecentExpired, "product-image", UUID.randomUUID(), UUID.randomUUID(),
                "key-recent-expired", "image/jpeg", 100,
                now.minus(Duration.ofMinutes(30)), now.minus(Duration.ofHours(1))
        );
        UploadSlot slotActive = new UploadSlot(
                idActive, "product-image", UUID.randomUUID(), UUID.randomUUID(),
                "key-active", "image/jpeg", 100,
                now.plus(Duration.ofMinutes(5)), now
        );

        slotStore.save(slotPastGrace);
        slotStore.save(slotRecentExpired);
        slotStore.save(slotActive);

        int cleaned = uploadSlots.cleanExpired();
        assertThat(cleaned).isEqualTo(1);

        assertThat(slotStore.findById(idPastGrace)).isEmpty();
        assertThat(slotStore.findById(idRecentExpired)).isPresent();
        assertThat(slotStore.findById(idActive)).isPresent();
        assertThat(store.deleted).contains("products/key-past-grace");
    }

    @Test
    void cleanExpired_xoaDoiTuongLoiVanXoaDong() {
        Instant now = baseTime;
        clock.set(now);

        UUID idPastGrace = UUID.randomUUID();
        UploadSlot slotPastGrace = new UploadSlot(
                idPastGrace, "product-image", UUID.randomUUID(), UUID.randomUUID(),
                "key-past-grace", "image/jpeg", 100,
                now.minus(Duration.ofHours(2)), now.minus(Duration.ofHours(3))
        );
        slotStore.save(slotPastGrace);

        store.failing = true;

        int cleaned = uploadSlots.cleanExpired();
        assertThat(cleaned).isEqualTo(1);
        assertThat(slotStore.findById(idPastGrace)).isEmpty();
    }
}
