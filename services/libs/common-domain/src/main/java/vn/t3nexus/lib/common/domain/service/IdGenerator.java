package vn.t3nexus.lib.common.domain.service;

import java.util.UUID;

/**
 * Cổng sinh mã thực thể (ADR-0001): UUID v7, sinh ở ứng dụng.
 * <br>Miền khai báo cổng; use case gọi rồi truyền mã vào aggregate.
 */
@FunctionalInterface
public interface IdGenerator {
    UUID generate();
}
