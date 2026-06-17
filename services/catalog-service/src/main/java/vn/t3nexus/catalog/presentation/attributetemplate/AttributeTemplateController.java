package vn.t3nexus.catalog.presentation.attributetemplate;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.t3nexus.catalog.application.attributetemplate.*;
import vn.t3nexus.catalog.presentation.attributetemplate.model.*;
import vn.t3nexus.lib.web.commons.response.ApiResponse;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AttributeTemplateController {

    private final CreateAttributeTemplate createAttributeTemplate;
    private final UpdateAttributeTemplate updateAttributeTemplate;
    private final AddAttributeOption addAttributeOption;
    private final UpdateAttributeOption updateAttributeOption;
    private final DeactivateAttributeOption deactivateAttributeOption;
    private final ListAttributeTemplates listAttributeTemplates;

    @GetMapping("/api/admin/attribute-templates")
    public ApiResponse<List<AttributeTemplateResponse>> listTemplates() {
        List<AttributeTemplateResponse> data = listAttributeTemplates.handle(new ListAttributeTemplates.Query())
                .templates().stream()
                .map(this::toResponse)
                .toList();
        return ApiResponse.ok(data);
    }

    @PostMapping("/api/admin/attribute-templates")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AttributeTemplateResponse> createTemplate(
            @Valid @RequestBody CreateAttributeTemplateRequest request) {
        CreateAttributeTemplate.Result result = createAttributeTemplate.handle(
                new CreateAttributeTemplate.Command(
                        request.name(), request.displayName(), request.inputType(), request.scope()));
        return ApiResponse.ok(new AttributeTemplateResponse(
                result.id(), request.name(), request.displayName(),
                request.inputType(), request.scope(), List.of()));
    }

    @PutMapping("/api/admin/attribute-templates/{id}")
    public ApiResponse<Void> updateTemplate(@PathVariable String id,
                                            @Valid @RequestBody UpdateAttributeTemplateRequest request) {
        updateAttributeTemplate.handle(new UpdateAttributeTemplate.Command(id, request.displayName()));
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/admin/attribute-templates/{id}/options")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AttributeOptionResponse> addOption(@PathVariable String id,
                                                          @Valid @RequestBody AddAttributeOptionRequest request) {
        AddAttributeOption.Result result = addAttributeOption.handle(
                new AddAttributeOption.Command(id, request.value(), request.displayValue()));
        return ApiResponse.ok(new AttributeOptionResponse(
                result.optionId(), request.value(), request.displayValue(), null));
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

    private AttributeTemplateResponse toResponse(ListAttributeTemplates.AttributeTemplateDetail detail) {
        List<AttributeOptionResponse> options = detail.options().stream()
                .map(o -> new AttributeOptionResponse(o.id(), o.value(), o.displayValue(), o.status()))
                .toList();
        return new AttributeTemplateResponse(
                detail.id(), detail.name(), detail.displayName(),
                detail.inputType(), detail.scope(), options);
    }
}
