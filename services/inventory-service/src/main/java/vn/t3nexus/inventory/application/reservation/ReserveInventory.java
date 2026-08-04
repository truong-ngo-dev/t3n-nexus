package vn.t3nexus.inventory.application.reservation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReserveInventory {

    private final StockRepository stockRepository;
    private final ReservationRepository reservationRepository;
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

        // DB reservation with pessimistic lock — limited offer (Redis) slot check removed from this
        // flow for now; SlotService/LimitedOffer domain kept as-is, not wired in until that path is
        // revisited (denormalized hasActiveLimitedOffer flag on Stock, see earlier analysis).
        List<ReservationItem> reservationItems = new ArrayList<>();
        for (Command.Item item : sortedItems) {
            Stock stock;
            try {
                stock = stockRepository.findBySkuIdForUpdate(item.skuId()).orElseThrow(StockException::notFound);
                stock.reserve(item.qty());
            } catch (DomainException e) {
                throw new ReservationFailedException(item.skuId(), e.getErrorCode().defaultMessage());
            }
            stockRepository.save(stock);
            eventDispatcher.dispatchAll(stock.getDomainEvents());
            stock.clearDomainEvents();
            reservationItems.add(ReservationItem.create(ReservationItemId.of(ulidGenerator.generate()), item.skuId(), item.qty()));
        }

        ReservationId id = ReservationId.of(ulidGenerator.generate());
        Reservation reservation = Reservation.create(id, command.orderId(), reservationItems);
        try {
            reservationRepository.save(reservation);
        } catch (DataIntegrityViolationException e) {
            // Concurrent duplicate: another transaction already reserved this orderId (UNIQUE(order_id)
            // caught it). Must roll back — this attempt's stock.reserve() writes above are NOT valid,
            // committing them would double-reserve stock. setRollbackOnly() instead of rethrow: this is
            // a benign duplicate, not a real failure — no Kafka retry/DLQ needed, consumer just acks.
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            log.info("[ReserveInventory] duplicate orderId={} (concurrent), rolling back, skipping", command.orderId());
            return;
        }
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
