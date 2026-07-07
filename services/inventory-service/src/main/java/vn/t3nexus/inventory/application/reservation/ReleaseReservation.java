package vn.t3nexus.inventory.application.reservation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.inventory.domain.reservation.Reservation;
import vn.t3nexus.inventory.domain.reservation.ReservationRepository;
import vn.t3nexus.inventory.domain.stock.Stock;
import vn.t3nexus.inventory.domain.stock.StockRepository;
import vn.t3nexus.lib.common.application.EventDispatcher;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReleaseReservation {

    private final ReservationRepository reservationRepository;
    private final StockRepository stockRepository;
    private final EventDispatcher eventDispatcher;

    @Transactional
    public void handle(Command command) {
        Reservation reservation = reservationRepository.findByOrderId(command.orderId())
                .orElse(null);

        if (reservation == null) {
            log.info("[ReleaseReservation] no reservation found for orderId={}, no-op", command.orderId());
            return;
        }

        if (!reservation.isPending()) {
            log.info("[ReleaseReservation] reservation orderId={} is already {}, no-op",
                    command.orderId(), reservation.getStatus());
            return;
        }

        reservation.getItems().forEach(item -> {
            Stock stock = stockRepository.findBySkuId(item.getSkuId()).orElse(null);
            if (stock == null) {
                log.warn("[ReleaseReservation] stock not found for skuId={}, skipping", item.getSkuId());
                return;
            }
            stock.release(item.getQty());
            stockRepository.save(stock);
            eventDispatcher.dispatchAll(stock.getDomainEvents());
            stock.clearDomainEvents();
        });

        reservation.release();
        reservationRepository.save(reservation);
        eventDispatcher.dispatchAll(reservation.getDomainEvents());
        reservation.clearDomainEvents();

        log.info("[ReleaseReservation] released: reservationId={}, orderId={}, traceId={}",
                reservation.getId().getValue(), command.orderId(), MDC.get("traceId"));
    }

    public record Command(String orderId) {}
}
