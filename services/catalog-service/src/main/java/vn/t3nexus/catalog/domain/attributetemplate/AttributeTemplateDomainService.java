package vn.t3nexus.catalog.domain.attributetemplate;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.t3nexus.catalog.domain.category.Category;
import vn.t3nexus.catalog.domain.category.CategoryAttributeAssignment;
import vn.t3nexus.catalog.domain.category.CategoryErrorCode;
import vn.t3nexus.catalog.domain.category.CategoryId;
import vn.t3nexus.catalog.domain.category.CategoryRepository;
import vn.t3nexus.catalog.domain.product.ProductAttributeValue;
import vn.t3nexus.catalog.domain.product.ProductErrorCode;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttributeTemplateDomainService {

    private final CategoryRepository categoryRepository;
    private final AttributeTemplateRepository attributeTemplateRepository;

    // Chặn deactivate nếu template đang required ở bất kỳ category nào — tránh deadlock (category đòi
    // hỏi bắt buộc 1 attribute không còn dùng được, Seller không thể tạo Product hợp lệ nữa). Không chặn
    // nếu chỉ assign nhưng không required — trường hợp đó an toàn, Product bỏ qua nó bình thường.
    public void validateTemplateDeactivatable(AttributeTemplateId templateId) {
        if (categoryRepository.existsRequiredAssignmentByTemplateId(templateId)) {
            throw new DomainException(AttributeTemplateErrorCode.TEMPLATE_REQUIRED_BY_CATEGORY);
        }
    }

    /**
     * @param previouslyPersisted bản đã lưu trước đó (rỗng khi Create — không có gì để "grandfather").
     *   Entry/giá trị giống hệt bản cũ được miễn re-validate theo trạng thái ACTIVE HIỆN TẠI của
     *   template/option — tránh Seller bị kẹt khi Update 1 phần không liên quan (đổi giá, mô tả...) chỉ
     *   vì 1 giá trị cũ đã bị Admin deactivate sau khi Product tạo. Tham khảo Salesforce "inactive
     *   picklist values" (record cũ giữ nguyên giá trị đã inactive, chỉ chặn CHỌN MỚI giá trị đó) —
     *   xem service.md § Product attribute validation. required check KHÔNG được grandfather — đó là
     *   policy forward-looking (category yêu cầu mới), không phải dữ liệu cũ bị lỗi thời.
     */
    public void validateProductAttributes(
            CategoryId categoryId,
            List<ProductAttributeValue> submitted,
            List<ProductAttributeValue> previouslyPersisted) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new DomainException(CategoryErrorCode.CATEGORY_NOT_FOUND));

        List<CategoryAttributeAssignment> assignments = category.getAssignments();

        Set<String> allowedIds = assignments.stream()
                .map(a -> a.getAttributeTemplateId().getValue())
                .collect(Collectors.toSet());

        Map<String, List<String>> previousByTemplate = previouslyPersisted.stream()
                .collect(Collectors.toMap(v -> v.templateId().getValue(), ProductAttributeValue::values));

        Set<String> submittedIds = submitted.stream()
                .map(v -> v.templateId().getValue())
                .collect(Collectors.toSet());

        // Check không có templateId lạ — category (leaf) assign gì thì Product chỉ được dùng đúng đó,
        // không tự thêm ngoài (đã chốt qua thảo luận Amazon-style, không có custom attribute tự do).
        // Grandfather: templateId đã tồn tại ở bản cũ (category gỡ assignment sau khi Product đã khai)
        // thì miễn — không nên chặn Update không liên quan chỉ vì cấu hình category đổi sau đó.
        for (String templateId : submittedIds) {
            if (!allowedIds.contains(templateId) && !previousByTemplate.containsKey(templateId)) {
                throw new DomainException(ProductErrorCode.ATTRIBUTE_NOT_IN_CATEGORY);
            }
        }

        // Check required templates phải có giá trị — KHÔNG grandfather (forward-looking policy).
        for (CategoryAttributeAssignment assignment : assignments) {
            if (assignment.isRequired()) {
                String templateId = assignment.getAttributeTemplateId().getValue();
                boolean hasValue = submitted.stream()
                        .anyMatch(v -> v.templateId().getValue().equals(templateId));
                if (!hasValue) {
                    throw new DomainException(ProductErrorCode.REQUIRED_ATTRIBUTE_MISSING);
                }
            }
        }

        // Check giá trị submit cho attribute SELECT phải khớp option ACTIVE của đúng template đó — so
        // theo AttributeOptionId (Product giờ tham chiếu option bằng ID, không copy value string nữa —
        // xem service.md § Copy vs reference). 1 batch query duy nhất (findAllByIds).
        List<AttributeTemplateId> submittedTemplateIds = submitted.stream()
                .map(ProductAttributeValue::templateId)
                .distinct()
                .toList();
        Map<String, AttributeTemplate> templatesById = attributeTemplateRepository
                .findAllByIds(submittedTemplateIds).stream()
                .collect(Collectors.toMap(t -> t.getId().getValue(), t -> t));

        for (ProductAttributeValue submittedValue : submitted) {
            String templateId = submittedValue.templateId().getValue();
            AttributeTemplate template = templatesById.get(templateId);
            if (template == null) continue;

            // isVariantDefining là quyết định của Seller (không phải Admin), nhưng vẫn chỉ có ý nghĩa
            // với SELECT — TEXT/NUMBER/BOOLEAN không có AttributeOption nên không thể tham gia
            // Variant.combination. Đây là lỗi cấu trúc dữ liệu tự thân, không grandfather.
            if (submittedValue.isVariantDefining() && template.getInputType() != InputType.SELECT) {
                throw new DomainException(ProductErrorCode.VARIANT_DEFINING_REQUIRES_SELECT);
            }

            List<String> previousValues = previousByTemplate.getOrDefault(templateId, List.of());

            for (String value : submittedValue.values()) {
                // Grandfather ở cấp TỪNG GIÁ TRỊ — giá trị y hệt bản cũ thì miễn toàn bộ check bên dưới,
                // dù các giá trị KHÁC trong cùng entry là mới (VD thêm 1 optionId vào list đã có sẵn).
                if (previousValues.contains(value)) continue;

                // Áp dụng cho mọi inputType — deactivate 1 template phải thực sự chặn được dùng tiếp
                // cho giá trị MỚI, không chỉ chặn assign mới vào category.
                if (template.getStatus() == AttributeTemplateStatus.INACTIVE) {
                    throw new DomainException(AttributeTemplateErrorCode.TEMPLATE_INACTIVE);
                }

                // NUMBER/BOOLEAN phải đúng format khai báo ở inputType — trước đây bất kỳ string nào
                // cũng lọt qua, làm mất hết ý nghĩa của việc khai inputType (sort/so sánh số sẽ vỡ nếu
                // lẫn giá trị không phải số). TEXT vẫn free-form, không có ràng buộc format nào.
                if (template.getInputType() == InputType.NUMBER) {
                    if (!isValidNumber(value)) {
                        throw new DomainException(ProductErrorCode.ATTRIBUTE_VALUE_FORMAT_INVALID);
                    }
                    continue;
                }
                if (template.getInputType() == InputType.BOOLEAN) {
                    if (!isValidBoolean(value)) {
                        throw new DomainException(ProductErrorCode.ATTRIBUTE_VALUE_FORMAT_INVALID);
                    }
                    continue;
                }
                if (template.getInputType() != InputType.SELECT) continue;

                boolean matchesActiveOption = template.getOptions().stream()
                        .anyMatch(o -> o.getStatus() == AttributeOptionStatus.ACTIVE
                                && o.getId().getValue().equals(value));
                if (!matchesActiveOption) {
                    throw new DomainException(ProductErrorCode.INVALID_ATTRIBUTE_VALUE);
                }
            }
        }
    }

    private static boolean isValidNumber(String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isValidBoolean(String value) {
        return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
    }
}
