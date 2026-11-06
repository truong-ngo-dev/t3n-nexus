package vn.t3nexus.catalog.presentation.attributetemplate;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.t3nexus.catalog.application.attributetemplate.ActivateAttributeOption;
import vn.t3nexus.catalog.application.attributetemplate.ActivateAttributeTemplate;
import vn.t3nexus.catalog.application.attributetemplate.AddAttributeOption;
import vn.t3nexus.catalog.application.attributetemplate.CreateAttributeTemplate;
import vn.t3nexus.catalog.application.attributetemplate.DeactivateAttributeOption;
import vn.t3nexus.catalog.application.attributetemplate.DeactivateAttributeTemplate;
import vn.t3nexus.catalog.application.attributetemplate.GetAttributeTemplate;
import vn.t3nexus.catalog.application.attributetemplate.ListAttributeTemplates;
import vn.t3nexus.catalog.application.attributetemplate.ReorderAttributeOptions;
import vn.t3nexus.catalog.application.attributetemplate.UpdateAttributeOption;
import vn.t3nexus.catalog.application.attributetemplate.UpdateAttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateStatus;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;
import vn.t3nexus.catalog.presentation.CatalogActorResolver;
import vn.t3nexus.catalog.presentation.attributetemplate.model.AddAttributeOptionRequest;
import vn.t3nexus.catalog.presentation.attributetemplate.model.AttributeTemplateResponse;
import vn.t3nexus.catalog.presentation.attributetemplate.model.AttributeTemplateSummaryResponse;
import vn.t3nexus.catalog.presentation.attributetemplate.model.CreateAttributeTemplateRequest;
import vn.t3nexus.catalog.presentation.attributetemplate.model.ReorderAttributeOptionsRequest;
import vn.t3nexus.catalog.presentation.attributetemplate.model.UpdateAttributeOptionRequest;
import vn.t3nexus.catalog.presentation.attributetemplate.model.UpdateAttributeTemplateRequest;
import vn.t3nexus.lib.web.commons.response.ApiResponse;

import java.util.List;
import java.util.UUID;

/** Quản mẫu thuộc tính và tùy chọn (CAP-CAT-02). Vai {@code ADMIN} kiểm trong use case; mọi đường cần đăng nhập. */
@RestController
@RequestMapping("/api/admin/attribute-templates")
@RequiredArgsConstructor
public class AttributeTemplateController {

    /** Trần kích thước trang; vượt thì cắt về trần. */
    private static final int MAX_PAGE_SIZE = 50;

    private final CreateAttributeTemplate createAttributeTemplate;
    private final UpdateAttributeTemplate updateAttributeTemplate;
    private final DeactivateAttributeTemplate deactivateAttributeTemplate;
    private final ActivateAttributeTemplate activateAttributeTemplate;
    private final AddAttributeOption addAttributeOption;
    private final UpdateAttributeOption updateAttributeOption;
    private final DeactivateAttributeOption deactivateAttributeOption;
    private final ActivateAttributeOption activateAttributeOption;
    private final ReorderAttributeOptions reorderAttributeOptions;
    private final ListAttributeTemplates listAttributeTemplates;
    private final GetAttributeTemplate getAttributeTemplate;

    @GetMapping
    public ApiResponse<AttributeTemplateSummaryResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) InputType inputType,
            @RequestParam(required = false) AttributeTemplateStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ListAttributeTemplates.Result result = listAttributeTemplates.handle(new ListAttributeTemplates.Query(
                CatalogActorResolver.from(jwt), keyword, inputType, status,
                Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE)));
        List<AttributeTemplateSummaryResponse.Item> items = result.items().stream()
                .map(s -> new AttributeTemplateSummaryResponse.Item(s.id(), s.name(), s.displayName(), s.inputType(),
                        s.unit(), s.status(), s.usable(), s.optionCount()))
                .toList();
        return ApiResponse.ok(new AttributeTemplateSummaryResponse(items, result.total()));
    }

    @GetMapping("/{id}")
    public ApiResponse<AttributeTemplateResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ok(getAttributeTemplate.handle(new GetAttributeTemplate.Query(CatalogActorResolver.from(jwt), id)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AttributeTemplateResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                         @Valid @RequestBody CreateAttributeTemplateRequest request) {
        return ok(createAttributeTemplate.handle(new CreateAttributeTemplate.Command(
                CatalogActorResolver.from(jwt), request.name(), request.displayName(), request.hint(),
                request.inputType(), request.unit())));
    }

    @PutMapping("/{id}")
    public ApiResponse<AttributeTemplateResponse> update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                         @RequestBody UpdateAttributeTemplateRequest request) {
        return ok(updateAttributeTemplate.handle(new UpdateAttributeTemplate.Command(
                CatalogActorResolver.from(jwt), id, request.displayName(), request.hint())));
    }

    @PostMapping("/{id}/deactivate")
    public ApiResponse<AttributeTemplateResponse> deactivate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ok(deactivateAttributeTemplate.handle(
                new DeactivateAttributeTemplate.Command(CatalogActorResolver.from(jwt), id)));
    }

    @PostMapping("/{id}/activate")
    public ApiResponse<AttributeTemplateResponse> activate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ok(activateAttributeTemplate.handle(
                new ActivateAttributeTemplate.Command(CatalogActorResolver.from(jwt), id)));
    }

    @PostMapping("/{id}/options")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AttributeTemplateResponse> addOption(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                            @RequestBody AddAttributeOptionRequest request) {
        return ok(addAttributeOption.handle(new AddAttributeOption.Command(
                CatalogActorResolver.from(jwt), id, request.value(), request.displayValue())));
    }

    @PutMapping("/{id}/options/order")
    public ApiResponse<AttributeTemplateResponse> reorderOptions(@AuthenticationPrincipal Jwt jwt,
                                                                 @PathVariable UUID id,
                                                                 @Valid @RequestBody ReorderAttributeOptionsRequest request) {
        return ok(reorderAttributeOptions.handle(new ReorderAttributeOptions.Command(
                CatalogActorResolver.from(jwt), id, request.optionIds())));
    }

    @PutMapping("/{id}/options/{optionId}")
    public ApiResponse<AttributeTemplateResponse> updateOption(@AuthenticationPrincipal Jwt jwt,
                                                               @PathVariable UUID id, @PathVariable UUID optionId,
                                                               @RequestBody UpdateAttributeOptionRequest request) {
        return ok(updateAttributeOption.handle(new UpdateAttributeOption.Command(
                CatalogActorResolver.from(jwt), id, optionId, request.displayValue())));
    }

    @PostMapping("/{id}/options/{optionId}/deactivate")
    public ApiResponse<AttributeTemplateResponse> deactivateOption(@AuthenticationPrincipal Jwt jwt,
                                                                   @PathVariable UUID id, @PathVariable UUID optionId) {
        return ok(deactivateAttributeOption.handle(
                new DeactivateAttributeOption.Command(CatalogActorResolver.from(jwt), id, optionId)));
    }

    @PostMapping("/{id}/options/{optionId}/activate")
    public ApiResponse<AttributeTemplateResponse> activateOption(@AuthenticationPrincipal Jwt jwt,
                                                                 @PathVariable UUID id, @PathVariable UUID optionId) {
        return ok(activateAttributeOption.handle(
                new ActivateAttributeOption.Command(CatalogActorResolver.from(jwt), id, optionId)));
    }

    private static ApiResponse<AttributeTemplateResponse> ok(vn.t3nexus.catalog.application.attributetemplate.TemplateView view) {
        return ApiResponse.ok(AttributeTemplateResponse.from(view));
    }
}
