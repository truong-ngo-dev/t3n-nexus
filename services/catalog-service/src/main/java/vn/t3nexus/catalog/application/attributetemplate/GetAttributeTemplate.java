package vn.t3nexus.catalog.application.attributetemplate;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateErrorCode;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateRepository;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.util.UUID;

/** Chi tiết mẫu cho quản trị: mọi trạng thái, kèm mọi tùy chọn và cờ "dùng được". */
@Service
@RequiredArgsConstructor
public class GetAttributeTemplate implements QueryHandler<GetAttributeTemplate.Query, TemplateView> {

    private final AttributeTemplateRepository repository;

    @Override
    @Transactional(readOnly = true)
    public TemplateView handle(Query query) {
        query.actor().requireAdmin();
        AttributeTemplate template = repository.findById(AttributeTemplateId.of(query.templateId()))
                .orElseThrow(() -> new DomainException(AttributeTemplateErrorCode.ATTRIBUTE_TEMPLATE_NOT_FOUND));
        return TemplateView.of(template, template.version());
    }

    public record Query(CatalogActor actor, UUID templateId) {}
}
