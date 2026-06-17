package vn.t3nexus.catalog.domain.variant;

import vn.t3nexus.catalog.domain.attributetemplate.AttributeOptionId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.lib.common.domain.model.ValueObject;

public record VariantAttributePair(
        AttributeTemplateId templateId,
        AttributeOptionId optionId
) implements ValueObject {}
