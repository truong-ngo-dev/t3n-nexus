package vn.t3nexus.inventory.domain.stock;

import vn.t3nexus.lib.common.domain.model.AbstractAggregateRoot;
import vn.t3nexus.lib.common.domain.model.AggregateRoot;

import java.time.Instant;

public class Stock extends AbstractAggregateRoot<StockId> implements AggregateRoot<StockId> {

    private final String skuId;
    private final String productId;
    private final String sellerId;
    private int totalQuantity;
    private int reservedQuantity;
    private boolean sellerActive;
    private boolean productPublished;
    private boolean adminBlocked;
    private int lowStockThreshold;
    private final Instant createdAt;
    private Instant updatedAt;

    private Stock(StockId id, String skuId, String productId, String sellerId,
                  int totalQuantity, int reservedQuantity, boolean sellerActive, boolean productPublished,
                  boolean adminBlocked, int lowStockThreshold, Instant createdAt, Instant updatedAt) {
        setId(id);
        this.skuId             = skuId;
        this.productId         = productId;
        this.sellerId          = sellerId;
        this.totalQuantity     = totalQuantity;
        this.reservedQuantity  = reservedQuantity;
        this.sellerActive      = sellerActive;
        this.productPublished  = productPublished;
        this.adminBlocked      = adminBlocked;
        this.lowStockThreshold = lowStockThreshold;
        this.createdAt         = createdAt;
        this.updatedAt         = updatedAt;
    }

    // ───────────── Factory Methods ─────────────

    public static Stock initialize(StockId id, String skuId, String productId, String sellerId,
                                   boolean sellerActive, boolean productPublished) {
        Instant now = Instant.now();
        return new Stock(id, skuId, productId, sellerId, 0, 0, sellerActive, productPublished, false, 0, now, now);
    }

    public static Stock reconstitute(StockId id, String skuId, String productId, String sellerId,
                                     int totalQuantity, int reservedQuantity,
                                     boolean sellerActive, boolean productPublished, boolean adminBlocked,
                                     int lowStockThreshold, Instant createdAt, Instant updatedAt) {
        return new Stock(id, skuId, productId, sellerId, totalQuantity, reservedQuantity,
                sellerActive, productPublished, adminBlocked, lowStockThreshold, createdAt, updatedAt);
    }

    // ───────────── Behaviour ─────────────

    public void setQuantity(int newTotalQty) {
        if (newTotalQty < 0) throw StockException.invalidQuantity();
        int prevAvailable = availableQuantity();
        this.totalQuantity = newTotalQty;
        this.updatedAt     = Instant.now();
        int newAvailable = availableQuantity();
        if (prevAvailable <= lowStockThreshold && newAvailable > lowStockThreshold) {
            addDomainEvent(new StockReplenishedEvent(getId().getValue(), skuId, sellerId, newAvailable));
        } else if (prevAvailable > lowStockThreshold && newAvailable <= lowStockThreshold) {
            addDomainEvent(new StockDepletedEvent(getId().getValue(), skuId, sellerId));
        }
    }

    public void reserve(int qty) {
        if (!isSellable())             throw StockException.inactive();
        if (availableQuantity() < qty) throw StockException.insufficientStock(skuId, qty, availableQuantity());
        int prevAvailable = availableQuantity();
        this.reservedQuantity += qty;
        this.updatedAt = Instant.now();
        int newAvailable = availableQuantity();
        if (prevAvailable > lowStockThreshold && newAvailable <= lowStockThreshold) {
            addDomainEvent(new StockDepletedEvent(getId().getValue(), skuId, sellerId));
        }
    }

    public void release(int qty) {
        int prevAvailable = availableQuantity();
        this.reservedQuantity = Math.max(0, this.reservedQuantity - qty);
        this.updatedAt = Instant.now();
        int newAvailable = availableQuantity();
        if (prevAvailable <= lowStockThreshold && newAvailable > lowStockThreshold) {
            addDomainEvent(new StockReplenishedEvent(getId().getValue(), skuId, sellerId, newAvailable));
        }
    }

    public void activate() {
        this.sellerActive = true;
        this.updatedAt    = Instant.now();
    }

    public void deactivate() {
        this.sellerActive = false;
        this.updatedAt    = Instant.now();
    }

    public void publishProduct() {
        this.productPublished = true;
        this.updatedAt        = Instant.now();
    }

    public void unpublishProduct() {
        this.productPublished = false;
        this.updatedAt        = Instant.now();
    }

    public void block() {
        this.adminBlocked = true;
        this.updatedAt    = Instant.now();
    }

    public void unblock() {
        this.adminBlocked = false;
        this.updatedAt    = Instant.now();
    }

    public void setLowStockThreshold(int threshold) {
        if (threshold < 0) throw StockException.invalidQuantity();
        this.lowStockThreshold = threshold;
        this.updatedAt         = Instant.now();
    }

    // ───────────── Computed ─────────────

    public int availableQuantity() {
        return totalQuantity - reservedQuantity;
    }

    public boolean isSellable() {
        return sellerActive && productPublished && !adminBlocked;
    }

    // ───────────── Getters ─────────────

    public String getSkuId()          { return skuId; }
    public String getProductId()      { return productId; }
    public String getSellerId()       { return sellerId; }
    public int getTotalQuantity()     { return totalQuantity; }
    public int getReservedQuantity()  { return reservedQuantity; }
    public boolean isSellerActive()   { return sellerActive; }
    public boolean isProductPublished() { return productPublished; }
    public boolean isAdminBlocked()   { return adminBlocked; }
    public int getLowStockThreshold() { return lowStockThreshold; }
    public Instant getCreatedAt()     { return createdAt; }
    public Instant getUpdatedAt()     { return updatedAt; }
}
