package vn.t3nexus.inventory.application.limitedoffer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.inventory.domain.limitedoffer.LimitedOffer;
import vn.t3nexus.inventory.domain.limitedoffer.LimitedOfferId;
import vn.t3nexus.inventory.domain.limitedoffer.LimitedOfferRepository;
import vn.t3nexus.inventory.domain.limitedoffer.SlotService;
import vn.t3nexus.inventory.domain.stock.StockException;
import vn.t3nexus.inventory.domain.stock.StockRepository;
import vn.t3nexus.lib.common.domain.service.ULIDGenerator;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivateLimitedOffer {

    private final StockRepository stockRepository;
    private final LimitedOfferRepository limitedOfferRepository;
    private final SlotService slotService;
    private final ULIDGenerator ulidGenerator;

    @Transactional
    public Result handle(Command command) {
        verifyOwnership(command.skuId(), command.sellerId());

        LimitedOffer offer = limitedOfferRepository.findBySkuId(command.skuId())
                .orElseGet(() -> LimitedOffer.create(
                        LimitedOfferId.of(ulidGenerator.generate()),
                        command.skuId(), command.maxQuantity(), command.windowSeconds()));

        offer.activate(command.maxQuantity(), command.windowSeconds());

        slotService.initSlots(command.skuId(), command.maxQuantity());
        limitedOfferRepository.save(offer);

        log.info("[ActivateLimitedOffer] skuId={} maxQty={} window={}s traceId={}",
                command.skuId(), command.maxQuantity(), command.windowSeconds(), MDC.get("traceId"));

        return new Result(offer.getId().getValue(), offer.getSkuId(),
                offer.getMaxQuantity(), offer.getWindowSeconds());
    }

    private void verifyOwnership(String skuId, String sellerId) {
        String actualSellerId = stockRepository.findBySkuId(skuId)
                .orElseThrow(StockException::notFound)
                .getSellerId();
        if (!actualSellerId.equals(sellerId)) {
            throw StockException.accessDenied();
        }
    }

    public record Command(String skuId, String sellerId, int maxQuantity, int windowSeconds) {}

    public record Result(String offerId, String skuId, int maxQuantity, int windowSeconds) {}
}
