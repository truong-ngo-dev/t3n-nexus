package vn.t3nexus.catalog.infrastructure.crosscutting.cache;

public final class CacheNames {

    private CacheNames() {}

    // ── L2 (Redis) only ──────────────────────────────────────
    public static final String CATEGORY_ATTRIBUTES  = "categories:attributes:v3"; // v3: filterable/searchable -> constraints (bỏ qua entry cũ trong Redis)

    // ── L1 (Caffeine) + L2 (Redis) ───────────────────────────
    public static final String CATEGORY_TREE        = "category:tree";
    public static final String PRODUCT              = "product";
    public static final String PRODUCT_VARIANTS     = "product-variants";

    // ── Pub/Sub channel ──────────────────────────────────────
    public static final String INVALIDATION_CHANNEL = "catalog:cache:invalidate";

    // ── Message format helpers ────────────────────────────────

    /** Tạo message evict một key cụ thể: "cacheName::key" */
    public static String evictMessage(String cacheName, String key) {
        return cacheName + "::" + key;
    }

    /** Tạo message clear toàn bộ cache: "cacheName" */
    public static String clearMessage(String cacheName) {
        return cacheName;
    }
}
