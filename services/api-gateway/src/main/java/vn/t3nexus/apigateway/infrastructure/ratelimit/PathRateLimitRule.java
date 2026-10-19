package vn.t3nexus.apigateway.infrastructure.ratelimit;

import java.time.Duration;

/**
 * Một luật giới hạn theo đường dẫn đã kiểm hợp lệ (bất biến).
 *
 * @param name     tên luật, duy nhất, nằm trong khóa Redis {@code ratelimit:rule:<name>:<địa chỉ>}
 * @param hostName tên máy đã quy đổi từ khóa host (không cổng, chữ thường)
 * @param method   phương thức, chữ hoa
 * @param path     đường dẫn đã chuẩn hóa (bỏ dấu {@code /} cuối)
 * @param limit    số yêu cầu tối đa trong cửa sổ
 * @param window   độ dài cửa sổ
 */
public record PathRateLimitRule(String name, String hostName, String method, String path, int limit, Duration window) {
}
