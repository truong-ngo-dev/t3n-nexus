package vn.t3nexus.oauth2.infrastructure.security.extension.session;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import vn.t3nexus.oauth2.application.login_session.resolve_authorization_session.ResolveAuthorizationSession;

import java.util.Optional;

/**
 * Đọc kết quả của cửa kiểm hạn từ thuộc tính của yêu cầu hiện tại ({@link #ATTRIBUTE}), do bộ lọc cửa kiểm hạn đặt. Thuộc
 * tính chỉ sống trong một yêu cầu nên không lẫn giữa các người dùng.
 */
public class RequestAttributeAuthorizationSessionSource implements AuthorizationSessionSource {

    public static final String ATTRIBUTE = RequestAttributeAuthorizationSessionSource.class.getName() + ".RESULT";

    @Override
    public Optional<ResolveAuthorizationSession.Result> current() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) return Optional.empty();
        Object value = attributes.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        return value instanceof ResolveAuthorizationSession.Result result ? Optional.of(result) : Optional.empty();
    }
}
