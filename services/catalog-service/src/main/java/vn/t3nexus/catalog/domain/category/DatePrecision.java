package vn.t3nexus.catalog.domain.category;

/** Độ chính xác của giá trị DATE — YEAR thô nhất, DAY chi tiết nhất. Dùng chung cho constraints.inputPrecision và
 *  discovery.filter.step (AGG-CAT-01, AGG-CAT-03). */
public enum DatePrecision {
    YEAR, MONTH, DAY;

    /** Thô hơn hoặc bằng {@code other} — dùng để guard step (filter) so với inputPrecision (dữ liệu thật). */
    public boolean isCoarserThanOrEqualTo(DatePrecision other) {
        return this.ordinal() <= other.ordinal();
    }
}
