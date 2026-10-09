package vn.t3nexus.catalog.presentation.attributetemplate;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.t3nexus.catalog.application.attributetemplate.*;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateStatus;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;
import vn.t3nexus.catalog.presentation.attributetemplate.model.*;
import vn.t3nexus.lib.web.commons.response.ApiResponse;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AttributeTemplateController {

    private static final int MAX_PAGE_SIZE = 100;

    private final CreateAttributeTemplate createAttributeTemplate;
    private final UpdateAttributeTemplate updateAttributeTemplate;
    private final AddAttributeOption addAttributeOption;
    private final UpdateAttributeOption updateAttributeOption;
    private final ReorderAttributeOptions reorderAttributeOptions;
    private final DeactivateAttributeOption deactivateAttributeOption;
    private final DeactivateAttributeTemplate deactivateAttributeTemplate;
    private final ActivateAttributeTemplate activateAttributeTemplate;
    private final ActivateAttributeOption activateAttributeOption;
    private final ListAttributeTemplates listAttributeTemplates;
    private final GetAttributeTemplate getAttributeTemplate;

    @GetMapping("/api/admin/attribute-templates")
    public ApiResponse<AttributeTemplateSummaryResponse> listTemplates(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) InputType inputType,
            @RequestParam(required = false) AttributeTemplateStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ListAttributeTemplates.Result result = listAttributeTemplates.handle(new ListAttributeTemplates.Query(
                keyword, inputType, status, Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE)));
        List<AttributeTemplateSummaryResponse.Item> items = result.items().stream()
                .map(s -> new AttributeTemplateSummaryResponse.Item(
                        s.id(), s.name(), s.displayName(), s.inputType(), s.unit(), s.status(), s.optionCount()))
                .toList();
        return ApiResponse.ok(new AttributeTemplateSummaryResponse(items, result.total()));
    }

    @GetMapping("/api/admin/attribute-templates/{id}")
    public ApiResponse<AttributeTemplateResponse> getTemplate(@PathVariable String id) {
        GetAttributeTemplate.Result t = getAttributeTemplate.handle(new GetAttributeTemplate.Query(id));
        List<AttributeOptionResponse> options = t.options().stream()
                .map(o -> new AttributeOptionResponse(o.id(), o.value(), o.displayValue(), o.status(), o.sortOrder()))
                .toList();
        return ApiResponse.ok(new AttributeTemplateResponse(
                t.id(), t.name(), t.displayName(), t.hint(), t.inputType(), t.unit(), t.status(), t.usable(), options));
    }

    @PostMapping("/api/admin/attribute-templates")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<String> createTemplate(@Valid @RequestBody CreateAttributeTemplateRequest request) {
        CreateAttributeTemplate.Result result = createAttributeTemplate.handle(
                new CreateAttributeTemplate.Command(
                        request.name(), request.displayName(), blankToNull(request.hint()),
                        request.inputType(), blankToNull(request.unit())));
        return ApiResponse.ok(result.id());
    }

    @PutMapping("/api/admin/attribute-templates/{id}")
    public ApiResponse<Void> updateTemplate(@PathVariable String id,
                                            @Valid @RequestBody UpdateAttributeTemplateRequest request) {
        updateAttributeTemplate.handle(new UpdateAttributeTemplate.Command(
                id, request.displayName(), blankToNull(request.hint())));
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/api/admin/attribute-templates/{id}")
    public ApiResponse<Void> deactivateTemplate(@PathVariable String id) {
        deactivateAttributeTemplate.handle(new DeactivateAttributeTemplate.Command(id));
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/admin/attribute-templates/{id}/activate")
    public ApiResponse<Void> activateTemplate(@PathVariable String id) {
        activateAttributeTemplate.handle(new ActivateAttributeTemplate.Command(id));
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/admin/attribute-templates/{id}/options")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<String> addOption(@PathVariable String id,
                                         @Valid @RequestBody AddAttributeOptionRequest request) {
        AddAttributeOption.Result result = addAttributeOption.handle(
                new AddAttributeOption.Command(id, request.value().trim(), request.displayValue()));
        return ApiResponse.ok(result.optionId());
    }

    @PutMapping("/api/admin/attribute-templates/{id}/options/order")
    public ApiResponse<Void> reorderOptions(@PathVariable String id,
                                            @Valid @RequestBody ReorderAttributeOptionsRequest request) {
        reorderAttributeOptions.handle(new ReorderAttributeOptions.Command(id, request.optionIds()));
        return ApiResponse.ok(null);
    }

    @PutMapping("/api/admin/attribute-templates/{id}/options/{optionId}")
    public ApiResponse<Void> updateOption(@PathVariable String id, @PathVariable String optionId,
                                          @Valid @RequestBody UpdateAttributeOptionRequest request) {
        updateAttributeOption.handle(new UpdateAttributeOption.Command(id, optionId, request.displayValue()));
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/api/admin/attribute-templates/{id}/options/{optionId}")
    public ApiResponse<Void> deactivateOption(@PathVariable String id, @PathVariable String optionId) {
        deactivateAttributeOption.handle(new DeactivateAttributeOption.Command(id, optionId));
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/admin/attribute-templates/{id}/options/{optionId}/activate")
    public ApiResponse<Void> activateOption(@PathVariable String id, @PathVariable String optionId) {
        activateAttributeOption.handle(new ActivateAttributeOption.Command(id, optionId));
        return ApiResponse.ok(null);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
