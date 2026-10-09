package vn.t3nexus.catalog.application.attributetemplate;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateRepository;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateStatus;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;

import java.util.List;

/** Danh sách quản trị: mọi trạng thái, có lọc + phân trang, trả bản tóm tắt (không kèm option). */
@Service
@RequiredArgsConstructor
public class ListAttributeTemplates implements QueryHandler<ListAttributeTemplates.Query, ListAttributeTemplates.Result> {

    private final AttributeTemplateRepository templateRepository;

    @Override
    @Transactional(readOnly = true)
    public Result handle(Query query) {
        List<TemplateSummary> items = templateRepository
                .search(query.keyword(), query.inputType(), query.status(), query.page(), query.size()).stream()
                .map(t -> new TemplateSummary(
                        t.getId().getValue(),
                        t.getName(),
                        t.getDisplayName(),
                        t.getInputType(),
                        t.getUnit(),
                        t.getStatus(),
                        t.getOptions().size()))
                .toList();
        long total = templateRepository.count(query.keyword(), query.inputType(), query.status());
        return new Result(items, total);
    }

    public record Query(String keyword, InputType inputType, AttributeTemplateStatus status, int page, int size) {}

    public record Result(List<TemplateSummary> items, long total) {}

    public record TemplateSummary(
            String id,
            String name,
            String displayName,
            InputType inputType,
            String unit,
            AttributeTemplateStatus status,
            int optionCount
    ) {}
}
