package vn.t3nexus.order.presentation.dev;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vn.t3nexus.lib.web.commons.response.ApiResponse;
import vn.t3nexus.order.application.order.CancelOrder;
import vn.t3nexus.order.application.order.ConfirmOrder;
import vn.t3nexus.order.application.order.CreateOrder;
import vn.t3nexus.order.application.order.GetOrder;
import vn.t3nexus.order.domain.order.OrderLineItem;

import java.util.List;

/**
 * Endpoint tạm để verify Event Sourcing round-trip (append -> replay) bằng Order thật,
 * chưa qua Kafka/Saga — dùng curl trực tiếp cho tới khi có OrderController/consumer thật.
 */
@RestController
@RequestMapping("/dev/orders")
@RequiredArgsConstructor
public class DevOrderController {

    private final CreateOrder createOrder;
    private final ConfirmOrder confirmOrder;
    private final CancelOrder cancelOrder;
    private final GetOrder getOrder;

    @PostMapping
    public ApiResponse<String> create(@RequestBody CreateOrderRequest request) {
        CreateOrder.Result result = createOrder.handle(new CreateOrder.Command(
                request.customerId(), request.sellerId(), request.items()));
        return ApiResponse.ok(result.orderId());
    }

    @PostMapping("/{orderId}/confirm")
    public ApiResponse<Void> confirm(@PathVariable String orderId) {
        confirmOrder.handle(new ConfirmOrder.Command(orderId));
        return ApiResponse.ok(null);
    }

    @PostMapping("/{orderId}/cancel")
    public ApiResponse<Void> cancel(@PathVariable String orderId, @RequestBody CancelOrderRequest request) {
        cancelOrder.handle(new CancelOrder.Command(orderId, request.reason()));
        return ApiResponse.ok(null);
    }

    @GetMapping("/{orderId}")
    public ApiResponse<GetOrder.Result> get(@PathVariable String orderId) {
        return ApiResponse.ok(getOrder.handle(new GetOrder.Query(orderId)));
    }

    public record CreateOrderRequest(String customerId, String sellerId, List<OrderLineItem> items) {}

    public record CancelOrderRequest(String reason) {}
}
