package vn.t3nexus.catalog.domain.attributetemplate;

import vn.t3nexus.lib.common.domain.model.AbstractId;
import vn.t3nexus.lib.common.domain.model.Id;

import java.util.UUID;

public class AttributeOptionId extends AbstractId<UUID> implements Id<UUID> {

    private AttributeOptionId(UUID value) {
        super(value);
    }

    public static AttributeOptionId of(UUID id) {
        return new AttributeOptionId(id);
    }
}
