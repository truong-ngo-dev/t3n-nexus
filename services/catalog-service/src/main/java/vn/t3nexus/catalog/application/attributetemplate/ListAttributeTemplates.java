package vn.t3nexus.catalog.application.attributetemplate;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateRepository;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateStatus;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;

import java.util.List;
import java.util.UUID;

/** Danh sách mẫu cho quản trị: mọi trạng thái, có lọc và phân trang, bản tóm tắt (không kèm tùy chọn). */
@Service
@RequiredArgsConstructor
public class ListAttributeTemplates implements QueryHandler<ListAttributeTemplates.Query, ListAttributeTemplates.Result> {

    private final AttributeTemplateRepository repository;

    @Override
    @Transactional(readOnly = true)
    public Result handle(Query query) {
        query.actor().requireAdmin();
        List<Summary> items = repository
                .search(query.keyword(), query.inputType(), query.status(), query.page(), query.size()).stream()
                .map(t -> new Summary(t.getId().getValue(), t.getName(), t.getDisplayName(), t.getInputType(),
                        t.getUnit(), t.getStatus(), t.isUsable(), t.getOptions().size()))
                .toList();
        return new Result(items, repository.count(query.keyword(), query.inputType(), query.status()));
    }

    public record Query(CatalogActor actor, String keyword, InputType inputType, AttributeTemplateStatus status,
                        int page, int size) {}

    public record Result(List<Summary> items, long total) {}

    public record Summary(UUID id, String name, String displayName, InputType inputType, String unit,
                          AttributeTemplateStatus status, boolean usable, int optionCount) {}
}
