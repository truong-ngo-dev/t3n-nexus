package vn.t3nexus.catalog.domain.category;

import java.util.List;

/**
 * Cách 1 thuộc tính tham gia bộ lọc tìm hàng (AGG-CAT-03). {@code kind} quyết định field nào có nghĩa:
 * <pre>
 *   SELECT   match, valueOrder                     — SINGLE_SELECT (match=ANY only) / MULTI_SELECT (ANY/ALL)
 *   BOOLEAN  (không field nào khác)                 — chỉ hiện "☐ &lt;tên thuộc tính&gt;" khi giá trị true
 *   RANGE    presentation, step (chỉ DATE+SLIDER), buckets (chỉ khi presentation=BUCKETS) — NUMBER/DATE
 * </pre>
 */
public record FilterConfig(
        Kind kind,
        MatchMode match,
        ValueOrder valueOrder,
        Presentation presentation,
        DatePrecision step,
        List<Bucket> buckets
) {
    public enum Kind { SELECT, BOOLEAN, RANGE }

    public enum MatchMode { ANY, ALL }

    public enum ValueOrder { OPTION_ORDER, COUNT }

    public enum Presentation { SLIDER, BUCKETS }

    /** Mốc số (NUMBER) hoặc mốc ngày tuyệt đối/tương đối, VD "now-30d" (DATE). Được chồng nhau; from &lt; to. */
    public record Bucket(String label, String from, String to) {}

    public static FilterConfig select(MatchMode match, ValueOrder valueOrder) {
        return new FilterConfig(Kind.SELECT, match, valueOrder, null, null, null);
    }

    public static FilterConfig booleanFilter() {
        return new FilterConfig(Kind.BOOLEAN, null, null, null, null, null);
    }

    public static FilterConfig range(Presentation presentation, DatePrecision step, List<Bucket> buckets) {
        return new FilterConfig(Kind.RANGE, null, null, presentation, step, buckets);
    }
}
