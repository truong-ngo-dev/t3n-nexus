package vn.t3nexus.storefrontwebbff.presentation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.session.ReactiveSessionRepository;
import org.springframework.session.Session;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import vn.t3nexus.storefrontwebbff.infrastructure.configuration.security.SessionMappingStore;

import java.util.Optional;
import java.util.UUID;

/**
 * Điểm nhận báo đăng xuất từ Xác thực ({@code {"sid"}}, feature 03): đăng xuất nơi khác, khóa tài khoản. Bảo vệ bằng token dịch vụ
 * phạm vi {@code storefront.internal} (xem {@code SecurityConfiguration.internalFilterChain}). Lũy đẳng: {@code sid} không còn ánh xạ
 * vẫn trả 200 (bên gửi không thử lại); {@code sid} không phải UUID là lỗi lập trình nên trả 400.
 */
@Slf4j
@RestController
@RequestMapping("/bff/internal/sessions")
@RequiredArgsConstructor
public class SessionRevokeController {

    private final SessionMappingStore                          mappings;
    private final ReactiveSessionRepository<? extends Session> sessionRepository;

    @PostMapping("/revoke")
    public Mono<ResponseEntity<Void>> revoke(@RequestBody RevokeRequest request) {
        if (!isUuid(request.sid())) {
            return Mono.just(ResponseEntity.badRequest().build());
        }
        // deleteById trả Mono<Void> (luôn rỗng khi xong) nên không dùng switchIfEmpty trên kết quả của nó; chọn nhánh theo việc có ánh xạ hay không
        return mappings.unlink(request.sid())
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(mapped -> {
                    if (mapped.isEmpty()) {
                        log.info("[SessionRevoke] no mapping (already gone or never existed): sid={}", request.sid());
                        return Mono.<Void>empty();
                    }
                    return sessionRepository.deleteById(mapped.get())
                            .doOnSuccess(done -> log.info("[SessionRevoke] cleared: sid={}, bffSession={}", request.sid(), mapped.get()));
                })
                .then(Mono.just(ResponseEntity.<Void>ok().build()));
    }

    private static boolean isUuid(String value) {
        if (value == null) return false;
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public record RevokeRequest(String sid) {}
}
