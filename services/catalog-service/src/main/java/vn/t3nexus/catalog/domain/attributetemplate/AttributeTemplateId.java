package vn.t3nexus.catalog.domain.attributetemplate;

import vn.t3nexus.lib.common.domain.model.AbstractId;
import vn.t3nexus.lib.common.domain.model.Id;

public class AttributeTemplateId extends AbstractId<String> implements Id<String> {

    private AttributeTemplateId(String value) {
        super(value);
    }

    public static AttributeTemplateId of(String id) {
        return new AttributeTemplateId(id);
    }
}
