package vn.t3nexus.catalog.application.attributetemplate;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeOptionStatus;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateErrorCode;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateRepository;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateStatus;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.util.List;

/** Chi tiết quản trị: đủ thuộc tính + MỌI option (kể cả đã tắt) theo thứ tự chuẩn. Không kèm danh mục đang gán. */
@Service
@RequiredArgsConstructor
public class GetAttributeTemplate implements QueryHandler<GetAttributeTemplate.Query, GetAttributeTemplate.Result> {

    private final AttributeTemplateRepository templateRepository;

    @Override
    @Transactional(readOnly = true)
    public Result handle(Query query) {
        AttributeTemplate t = templateRepository.findById(AttributeTemplateId.of(query.id()))
                .orElseThrow(() -> new DomainException(AttributeTemplateErrorCode.TEMPLATE_NOT_FOUND));

        List<OptionDetail> options = t.getOptionsInOrder().stream()
                .map(o -> new OptionDetail(o.getId().getValue(), o.getValue(), o.getDisplayValue(),
                        o.getStatus(), o.getSortOrder()))
                .toList();

        return new Result(t.getId().getValue(), t.getName(), t.getDisplayName(), t.getHint(), t.getInputType(),
                t.getUnit(), t.getStatus(), t.isUsable(), options);
    }

    public record Query(String id) {}

    public record Result(
            String id,
            String name,
            String displayName,
            String hint,
            InputType inputType,
            String unit,
            AttributeTemplateStatus status,
            boolean usable,
            List<OptionDetail> options
    ) {}

    public record OptionDetail(String id, String value, String displayValue, AttributeOptionStatus status,
                               int sortOrder) {}
}
