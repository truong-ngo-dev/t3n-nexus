package vn.t3nexus.catalog.domain.category;

import vn.t3nexus.lib.common.domain.model.AbstractId;
import vn.t3nexus.lib.common.domain.model.Id;

public class CategoryId extends AbstractId<String> implements Id<String> {

    private CategoryId(String value) {
        super(value);
    }

    public static CategoryId of(String id) {
        return new CategoryId(id);
    }
}
