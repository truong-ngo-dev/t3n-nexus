package vn.t3nexus.lib.upload;

import org.springframework.scheduling.annotation.Scheduled;

/** Việc dọn định kỳ: xóa chỗ hết hạn quá ân hạn cùng đối tượng của chúng. An toàn khi nhiều bản cùng chạy (xóa lặp vô hại). */
class UploadCleaner {

    private final UploadSlots slots;

    UploadCleaner(UploadSlots slots) {
        this.slots = slots;
    }

    @Scheduled(fixedDelayString = "${upload.clean-interval:PT10M}", initialDelayString = "${upload.clean-interval:PT10M}")
    void clean() {
        slots.cleanExpired();
    }
}
