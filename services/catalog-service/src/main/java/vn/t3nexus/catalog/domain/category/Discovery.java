package vn.t3nexus.catalog.domain.category;

/**
 * Cách 1 thuộc tính tham gia việc tìm hàng (AGG-CAT-03) — mỗi nhánh là một khả năng độc lập, bật/tắt riêng.
 * {@code search}: null hoặc trọng số cho full-text (không hỗ trợ DATE). {@code sort}: chỉ NUMBER/DATE.
 */
public record Discovery(SearchWeight search, FilterConfig filter, SortConfig sort) {

    public enum SearchWeight { NORMAL, HIGH }

    public static final Discovery NONE = new Discovery(null, null, null);
}
