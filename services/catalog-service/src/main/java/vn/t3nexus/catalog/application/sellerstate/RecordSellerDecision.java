package vn.t3nexus.catalog.application.sellerstate;

import org.springframework.stereotype.Service;
import vn.t3nexus.catalog.domain.product.SellerStateRepository;
import vn.t3nexus.catalog.domain.product.SellerStatus;

import java.time.Clock;
import java.util.UUID;

/**
 * Ghi kết quả duyệt hồ sơ người bán vào bản sao trạng thái (feature 09, T4). Ghi đè chỉ khi {@code version} hồ sơ mới
 * hơn bản đã lưu, nên nhận lặp và sai thứ tự đều vô hại. Trạng thái "Được duyệt" không bị sự kiện cũ hơn kéo lùi.
 */
@Service
public class RecordSellerDecision {

    private final SellerStateRepository states;
    private final Clock clock;

    public RecordSellerDecision(SellerStateRepository states, Clock clock) {
        this.states = states;
        this.clock = clock;
    }

    /** @return true nếu bản sao được ghi hoặc ghi đè; false nếu bỏ qua vì phiên bản không mới hơn */
    public boolean handle(Command command) {
        return states.applyDecision(command.accountId(), command.status(), command.version(), clock.instant());
    }

    public record Command(UUID accountId, SellerStatus status, long version) {
    }
}
