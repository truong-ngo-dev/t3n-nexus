package vn.t3nexus.inventory.domain.limitedoffer;

public interface SlotService {

    /** Sets the Redis slot counter to maxQuantity. Resets the Bloom Filter. */
    void initSlots(String skuId, int maxQuantity);

    /** Deletes the Redis slot counter key. */
    void clearSlots(String skuId);

    /**
     * Atomically checks and decrements the slot counter via Lua script.
     * Returns NO_LIMIT if no Redis key exists (SKU has no active limited offer),
     * SLOT_GRANTED if a slot was successfully taken, SOLD_OUT if exhausted.
     */
    SlotCheckResult tryDecrementSlot(String skuId);

    /** Increments the slot counter back — call when a granted slot must be returned on failure. */
    void returnSlot(String skuId);
}
