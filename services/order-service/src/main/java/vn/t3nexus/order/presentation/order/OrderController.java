package vn.t3nexus.order.presentation.order;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.t3nexus.lib.web.commons.response.ApiResponse;
import vn.t3nexus.order.application.order.CreateOrder;
import vn.t3nexus.order.application.order.GetOrder;
import vn.t3nexus.order.domain.order.OrderLineItem;
import vn.t3nexus.order.domain.order.ShippingAddress;
import vn.t3nexus.order.presentation.order.model.CreateOrderRequest;
import vn.t3nexus.order.presentation.order.model.OrderDetailResponse;
import vn.t3nexus.order.presentation.order.model.OrderResponse;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final CreateOrder createOrder;
    private final GetOrder getOrder;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        CreateOrder.Result result = createOrder.handle(new CreateOrder.Command(
                request.customerId(), request.sellerId(),
                request.items().stream()
                        .map(item -> new OrderLineItem(item.skuId(), item.qty(), item.unitPrice()))
                        .toList(),
                request.paymentMethod(),
                new ShippingAddress(
                        request.address().recipientName(), request.address().phone(),
                        request.address().addressLine(), request.address().ward(),
                        request.address().province(), request.address().note())));
        return ApiResponse.ok(new OrderResponse(result.orderId(), result.status()));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderDetailResponse> get(@PathVariable String orderId) {
        GetOrder.Result result = getOrder.handle(new GetOrder.Query(orderId));
        return ApiResponse.ok(OrderDetailResponse.from(result));
    }
}
