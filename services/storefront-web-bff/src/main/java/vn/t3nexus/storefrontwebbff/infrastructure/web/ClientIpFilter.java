package vn.t3nexus.storefrontwebbff.infrastructure.web;

import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import vn.t3nexus.lib.observability.http.ClientIpResolver;

import java.net.InetSocketAddress;

/**
 * Chuyển địa chỉ người gọi xuống dịch vụ sau trong {@code X-Client-Ip} (`tech.md` T7, R19). Giá trị là kết quả của {@link ClientIpResolver}: tiêu đề
 * do cổng đặt chỉ được giữ khi kết nối tới BFF từ proxy tin cậy (cổng của ta); từ nơi khác, hoặc giá trị không phải địa chỉ IP, thì thay bằng địa chỉ
 * kết nối. Nhờ vậy dịch vụ sau chỉ cần tin kết nối từ BFF mà không tin được tiêu đề do người ngoài gửi.
 */
@Component
@RequiredArgsConstructor
public class ClientIpFilter implements GlobalFilter, Ordered {

    private final ClientIpResolver resolver;

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        String remoteAddress = remote == null || remote.getAddress() == null ? null : remote.getAddress().getHostAddress();
        String resolved = resolver.resolve(remoteAddress, exchange.getRequest().getHeaders().getFirst(ClientIpResolver.HEADER));
        return chain.filter(exchange.mutate().request(r -> r.headers(h -> h.set(ClientIpResolver.HEADER, resolved))).build());
    }
}
