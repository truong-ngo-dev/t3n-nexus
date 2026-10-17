package vn.t3nexus.oauth2.infrastructure.security.extension.session;

import org.springframework.stereotype.Component;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/** Đọc tham số {@value #PARAMETER} của yêu cầu web hiện tại (BFF gửi kèm lúc làm mới token). */
@Component
public class RequestParameterSidSource implements RequestedSidSource {

    public static final String PARAMETER = "sid";

    @Override
    public Optional<String> current() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servlet)) return Optional.empty();
        String value = servlet.getRequest().getParameter(PARAMETER);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }
}
