package vn.t3nexus.catalog.domain.attributetemplate;

import vn.t3nexus.lib.common.domain.model.AbstractId;
import vn.t3nexus.lib.common.domain.model.Id;

public class AttributeOptionId extends AbstractId<String> implements Id<String> {

    private AttributeOptionId(String value) {
        super(value);
    }

    public static AttributeOptionId of(String id) {
        return new AttributeOptionId(id);
    }
}
