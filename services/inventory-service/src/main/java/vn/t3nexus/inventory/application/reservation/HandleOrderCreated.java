package vn.t3nexus.inventory.application.reservation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestration cho {@code OrderCreated} — rẽ nhánh T1 (reserve thành công) / T2 (hết hàng, ghi nhận
 * thất bại), đúng convention "consumer chỉ decode + delegate, business rule thuộc application layer"
 * (cùng lý do đã tách {@code HandleInventoryReserved} bên order-service, 2026-08-10). Trước đây nhánh
 * này nằm thẳng trong {@code OrderCreatedConsumer} (infra tự catch exception rồi tự quyết định gọi
 * handler nào tiếp theo) — vi phạm convention, chuyển vào đây.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HandleOrderCreated {

    private final ReserveInventory reserveInventory;
    private final RecordReservationFailure recordReservationFailure;

    public void handle(Command command) {
        try {
            reserveInventory.handle(new ReserveInventory.Command(
                    command.orderId(),
                    command.items().stream()
                            .map(item -> new ReserveInventory.Command.Item(item.skuId(), item.qty()))
                            .toList()));
        } catch (ReserveInventory.ReservationFailedException e) {
            log.warn("[HandleOrderCreated] reservation failed orderId={}, failedSkuId={}, reason={}",
                    command.orderId(), e.getFailedSkuId(), e.getReason());
            recordReservationFailure.handle(new RecordReservationFailure.Command(
                    command.orderId(),
                    command.items().stream()
                            .map(item -> new RecordReservationFailure.Command.Item(item.skuId(), item.qty()))
                            .toList(),
                    e.getFailedSkuId(),
                    e.getReason()));
        }
    }

    public record Command(String orderId, List<Item> items) {
        public record Item(String skuId, int qty) {}
    }
}
