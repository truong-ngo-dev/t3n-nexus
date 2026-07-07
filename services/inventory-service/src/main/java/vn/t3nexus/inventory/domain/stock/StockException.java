package vn.t3nexus.inventory.domain.stock;

import vn.t3nexus.lib.common.domain.exception.DomainException;

public final class StockException {

    private StockException() {}

    public static DomainException notFound() {
        return new DomainException(StockErrorCode.STOCK_NOT_FOUND);
    }

    public static DomainException alreadyExists() {
        return new DomainException(StockErrorCode.STOCK_ALREADY_EXISTS);
    }

    public static DomainException inactive() {
        return new DomainException(StockErrorCode.STOCK_INACTIVE);
    }

    public static DomainException insufficientStock(String skuId, int requested, int available) {
        return new DomainException(StockErrorCode.INSUFFICIENT_STOCK,
                "Insufficient stock for SKU " + skuId + ": requested=" + requested + ", available=" + available);
    }

    public static DomainException accessDenied() {
        return new DomainException(StockErrorCode.STOCK_ACCESS_DENIED);
    }

    public static DomainException invalidQuantity() {
        return new DomainException(StockErrorCode.INVALID_QUANTITY);
    }
}
