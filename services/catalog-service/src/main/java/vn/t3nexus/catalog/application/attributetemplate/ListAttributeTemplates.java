package vn.t3nexus.catalog.application.attributetemplate;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeOption;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeOptionStatus;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeScope;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateRepository;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListAttributeTemplates
        implements QueryHandler<ListAttributeTemplates.Query, ListAttributeTemplates.Result> {

    private final AttributeTemplateRepository templateRepository;

    @Override
    public Result handle(Query query) {
        List<AttributeTemplateDetail> templates = templateRepository.findAll().stream()
                .map(template -> new AttributeTemplateDetail(
                        template.getId().getValue(),
                        template.getName(),
                        template.getDisplayName(),
                        template.getInputType(),
                        template.getScope(),
                        template.getOptions().stream()
                                .map(option -> new AttributeOptionDetail(
                                        option.getId().getValue(),
                                        option.getValue(),
                                        option.getDisplayValue(),
                                        option.getStatus()))
                                .toList()))
                .toList();
        return new Result(templates);
    }

    public record Query() {}

    public record Result(List<AttributeTemplateDetail> templates) {}

    public record AttributeTemplateDetail(
            String id,
            String name,
            String displayName,
            InputType inputType,
            AttributeScope scope,
            List<AttributeOptionDetail> options) {}

    public record AttributeOptionDetail(
            String id,
            String value,
            String displayValue,
            AttributeOptionStatus status) {}
}
