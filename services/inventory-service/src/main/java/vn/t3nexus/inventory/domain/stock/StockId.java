package vn.t3nexus.inventory.domain.stock;

import vn.t3nexus.lib.common.domain.model.AbstractId;
import vn.t3nexus.lib.common.domain.model.Id;

public class StockId extends AbstractId<String> implements Id<String> {

    private StockId(String value) {
        super(value);
    }

    public static StockId of(String id) {
        return new StockId(id);
    }
}
