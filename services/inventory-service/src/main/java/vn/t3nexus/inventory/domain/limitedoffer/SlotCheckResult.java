package vn.t3nexus.inventory.domain.limitedoffer;

public enum SlotCheckResult {
    NO_LIMIT,     // No active limited offer for this SKU — proceed with normal DB reservation
    SLOT_GRANTED, // Slot decremented in Redis — proceed with DB reservation
    SOLD_OUT      // Slots exhausted — reject without hitting DB
}
