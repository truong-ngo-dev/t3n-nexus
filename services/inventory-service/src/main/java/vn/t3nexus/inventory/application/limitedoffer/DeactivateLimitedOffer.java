package vn.t3nexus.inventory.application.limitedoffer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.inventory.domain.limitedoffer.LimitedOffer;
import vn.t3nexus.inventory.domain.limitedoffer.LimitedOfferException;
import vn.t3nexus.inventory.domain.limitedoffer.LimitedOfferRepository;
import vn.t3nexus.inventory.domain.limitedoffer.SlotService;
import vn.t3nexus.inventory.domain.stock.StockException;
import vn.t3nexus.inventory.domain.stock.StockRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeactivateLimitedOffer {

    private final StockRepository stockRepository;
    private final LimitedOfferRepository limitedOfferRepository;
    private final SlotService slotService;

    @Transactional
    public void handle(Command command) {
        verifyOwnership(command.skuId(), command.sellerId());

        LimitedOffer offer = limitedOfferRepository.findBySkuId(command.skuId())
                .orElseThrow(LimitedOfferException::notFound);
        offer.end();  // domain guard: throws LIMITED_OFFER_NOT_ACTIVE if not ACTIVE

        slotService.clearSlots(command.skuId());
        limitedOfferRepository.save(offer);

        log.info("[DeactivateLimitedOffer] skuId={} traceId={}", command.skuId(), MDC.get("traceId"));
    }

    private void verifyOwnership(String skuId, String sellerId) {
        String actualSellerId = stockRepository.findBySkuId(skuId)
                .orElseThrow(StockException::notFound)
                .getSellerId();
        if (!actualSellerId.equals(sellerId)) {
            throw StockException.accessDenied();
        }
    }

    public record Command(String skuId, String sellerId) {}
}
