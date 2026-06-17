package vn.t3nexus.catalog.domain.product;

import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.lib.common.domain.model.ValueObject;

public record ProductAttributeValue(
        AttributeTemplateId templateId,
        String value
) implements ValueObject {}
