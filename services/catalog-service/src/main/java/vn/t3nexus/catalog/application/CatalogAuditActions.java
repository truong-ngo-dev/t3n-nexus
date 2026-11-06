package vn.t3nexus.catalog.application;

/** Tên hành động, đích và vai kiểm toán của danh mục hàng (tech.md T4 của feature 05). */
public final class CatalogAuditActions {

    public static final String ATTRIBUTE_CREATED = "ATTRIBUTE_CREATED";
    public static final String ATTRIBUTE_UPDATED = "ATTRIBUTE_UPDATED";
    public static final String ATTRIBUTE_DEACTIVATED = "ATTRIBUTE_DEACTIVATED";

    public static final String BRAND_CREATED = "BRAND_CREATED";
    public static final String BRAND_UPDATED = "BRAND_UPDATED";
    public static final String BRAND_DEACTIVATED = "BRAND_DEACTIVATED";

    public static final String CATEGORY_CREATED = "CATEGORY_CREATED";
    public static final String CATEGORY_UPDATED = "CATEGORY_UPDATED";
    public static final String CATEGORY_DEACTIVATED = "CATEGORY_DEACTIVATED";
    public static final String CATEGORY_DELETED = "CATEGORY_DELETED";

    public static final String TARGET_ATTRIBUTE_TEMPLATE = "ATTRIBUTE_TEMPLATE";

    public static final String TARGET_CATEGORY = "CATEGORY";
    public static final String TARGET_BRAND = "BRAND";

    public static final String ADMIN_ROLE = "ADMIN";

    /** Chạy trước handler hộp thư (số nhỏ chạy trước): lỗi kiểm toán rollback cả hai trong cùng giao dịch. */
    public static final int AUDIT_HANDLER_ORDER = 50;

    private CatalogAuditActions() {
    }
}
