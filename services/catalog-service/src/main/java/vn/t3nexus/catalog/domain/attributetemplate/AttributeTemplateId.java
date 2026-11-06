package vn.t3nexus.catalog.domain.attributetemplate;

import vn.t3nexus.lib.common.domain.model.AbstractId;
import vn.t3nexus.lib.common.domain.model.Id;

import java.util.UUID;

public class AttributeTemplateId extends AbstractId<UUID> implements Id<UUID> {

    private AttributeTemplateId(UUID value) {
        super(value);
    }

    public static AttributeTemplateId of(UUID id) {
        return new AttributeTemplateId(id);
    }
}
