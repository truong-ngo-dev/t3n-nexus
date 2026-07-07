package vn.t3nexus.inventory.application.reservation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.inventory.domain.limitedoffer.SlotCheckResult;
import vn.t3nexus.inventory.domain.limitedoffer.SlotService;
import vn.t3nexus.inventory.domain.reservation.Reservation;
import vn.t3nexus.inventory.domain.reservation.ReservationId;
import vn.t3nexus.inventory.domain.reservation.ReservationItem;
import vn.t3nexus.inventory.domain.reservation.ReservationItemId;
import vn.t3nexus.inventory.domain.reservation.ReservationRepository;
import vn.t3nexus.inventory.domain.stock.Stock;
import vn.t3nexus.inventory.domain.stock.StockException;
import vn.t3nexus.inventory.domain.stock.StockRepository;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.service.ULIDGenerator;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReserveInventory {

    private static final Duration RESERVATION_TTL = Duration.ofMinutes(30);

    private final StockRepository stockRepository;
    private final ReservationRepository reservationRepository;
    private final SlotService slotService;
    private final EventDispatcher eventDispatcher;
    private final ULIDGenerator ulidGenerator;

    @Transactional
    public void handle(Command command) {
        if (reservationRepository.existsByOrderId(command.orderId())) {
            log.info("[ReserveInventory] duplicate orderId={}, skipping", command.orderId());
            return;
        }

        List<Command.Item> sortedItems = command.items().stream()
                .sorted(Comparator.comparing(Command.Item::skuId))
                .toList();

        // Phase 1: limited offer slot check (Redis, before any DB write)
        List<String> grantedSlots = new ArrayList<>();
        try {
            for (Command.Item item : sortedItems) {
                SlotCheckResult slotResult = slotService.tryDecrementSlot(item.skuId());
                if (slotResult == SlotCheckResult.SOLD_OUT) {
                    throw new ReservationFailedException(item.skuId(), "Limited offer slots exhausted");
                }
                if (slotResult == SlotCheckResult.SLOT_GRANTED) {
                    grantedSlots.add(item.skuId());
                }
            }
        } catch (ReservationFailedException e) {
            grantedSlots.forEach(slotService::returnSlot);
            throw e;
        }

        // Phase 2: DB reservation with pessimistic lock
        List<ReservationItem> reservationItems = new ArrayList<>();
        try {
            for (Command.Item item : sortedItems) {
                Stock stock;
                try {
                    stock = stockRepository.findBySkuIdForUpdate(item.skuId())
                            .orElseThrow(StockException::notFound);
                    stock.reserve(item.qty());
                } catch (DomainException e) {
                    throw new ReservationFailedException(item.skuId(), e.getErrorCode().defaultMessage());
                }
                stockRepository.save(stock);
                eventDispatcher.dispatchAll(stock.getDomainEvents());
                stock.clearDomainEvents();
                reservationItems.add(ReservationItem.create(
                        ReservationItemId.of(ulidGenerator.generate()), item.skuId(), item.qty()));
            }
        } catch (ReservationFailedException e) {
            grantedSlots.forEach(slotService::returnSlot);
            throw e;
        }

        ReservationId id = ReservationId.of(ulidGenerator.generate());
        Reservation reservation = Reservation.create(id, command.orderId(), reservationItems,
                Instant.now().plus(RESERVATION_TTL));
        reservationRepository.save(reservation);
        eventDispatcher.dispatchAll(reservation.getDomainEvents());
        reservation.clearDomainEvents();

        log.info("[ReserveInventory] reserved: reservationId={}, orderId={}, items={}, traceId={}",
                id.getValue(), command.orderId(), reservationItems.size(), MDC.get("traceId"));
    }

    public record Command(String orderId, List<Item> items) {
        public record Item(String skuId, int qty) {}
    }

    public static class ReservationFailedException extends RuntimeException {
        private final String failedSkuId;
        private final String reason;

        public ReservationFailedException(String failedSkuId, String reason) {
            super("Reservation failed for skuId=" + failedSkuId + ": " + reason);
            this.failedSkuId = failedSkuId;
            this.reason      = reason;
        }

        public String getFailedSkuId() { return failedSkuId; }
        public String getReason()      { return reason; }
    }
}
