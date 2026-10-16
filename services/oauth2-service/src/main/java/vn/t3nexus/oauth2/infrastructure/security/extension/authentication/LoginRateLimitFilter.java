package vn.t3nexus.oauth2.infrastructure.security.extension.authentication;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.t3nexus.lib.ratelimiter.RateLimiter;
import vn.t3nexus.oauth2.domain.user_account.Email;
import vn.t3nexus.oauth2.domain.user_account.UserAccountException;
import vn.t3nexus.oauth2.infrastructure.cross_cutting.utils.IpAddressExtractor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

/**
 * Giới hạn số lần thử đăng nhập bằng mật khẩu (bước 10, `tech.md` T3): chặn đúng {@code POST /login}, <b>trước</b> khi tới bộ xác thực
 * nên vượt giới hạn thì không kiểm mật khẩu (không tốn một lần băm). Đếm <b>mọi lần thử</b> theo hai khóa, áp như nhau cho mọi
 * email kể cả email không tồn tại, nên trạng thái bị chặn không lộ email nào có thật (INV-ATH-03):
 * <ol>
 *   <li>cặp (email, địa chỉ mạng), mức thấp;</li>
 *   <li>riêng email, mức cao hơn. Chỉ đếm khi cặp chưa bị chặn, để một địa chỉ bị chặn không làm cạn mức của email với người dùng
 *       đang đăng nhập từ địa chỉ khác.</li>
 * </ol>
 * Email được chuẩn hóa như lúc xác thực ({@link Email}); giá trị sai định dạng vẫn bị đếm (theo dạng chữ thường đã cắt khoảng trắng) vì
 * mỗi lần thử vẫn tốn một lần băm. Khóa trong Redis dùng bản băm của email, không chứa email.
 *
 * <p>Vượt giới hạn: trả HTTP 429 kèm {@code Retry-After} (chuyển tiếp tới {@value #THROTTLED_PATH} để dựng lại trang đăng nhập). Bộ đếm
 * của thư viện chỉ trả đúng hay sai, không trả thời gian còn lại, nên {@code Retry-After} là độ dài cửa sổ (cận trên).
 * <b>Bộ đếm lỗi (Redis không dùng được) thì mở cửa</b> và ghi cảnh báo: đóng cửa sẽ khóa cả đăng nhập (đã chốt ở bước 10).
 *
 * <p>Địa chỉ mạng lấy qua {@link IpAddressExtractor}. Chừng nào cổng truy cập chưa ghi đè tiêu đề chuyển tiếp (feature 06), một client có thể
 * giả tiêu đề để né mức theo cặp; mức riêng email vẫn chặn được.
 */
@Slf4j
public class LoginRateLimitFilter extends OncePerRequestFilter {

    public static final String THROTTLED_PATH = "/login/throttled";
    /** Thuộc tính của request: số giây nên chờ trước khi thử lại ({@code Long}). */
    public static final String RETRY_AFTER_SECONDS = LoginRateLimitFilter.class.getName() + ".RETRY_AFTER_SECONDS";

    private static final String PAIR_PREFIX  = "login_pair:";
    private static final String EMAIL_PREFIX = "login_email:";

    private final RateLimiter rateLimiter;
    private final LoginRateLimitProperties properties;

    public LoginRateLimitFilter(RateLimiter rateLimiter, LoginRateLimitProperties properties) {
        this.rateLimiter = rateLimiter;
        this.properties  = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equalsIgnoreCase(request.getMethod()) && "/login".equals(request.getServletPath()));
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        Optional<Duration> wait = exceeded(normalize(request.getParameter("username")), IpAddressExtractor.extract(request));
        if (wait.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        log.warn("login attempts over the limit");
        request.setAttribute(RETRY_AFTER_SECONDS, wait.get().toSeconds());
        request.getRequestDispatcher(THROTTLED_PATH).forward(request, response);
    }

    /** Khoảng chờ khi bị chặn, rỗng khi cho qua (kể cả khi bộ đếm lỗi). */
    private Optional<Duration> exceeded(String email, String address) {
        try {
            String emailKey = digest(email);
            if (!rateLimiter.tryAcquire(PAIR_PREFIX + emailKey + ":" + digest(address),
                    properties.pairAttempts(), properties.pairWindow())) {
                return Optional.of(properties.pairWindow());
            }
            if (!rateLimiter.tryAcquire(EMAIL_PREFIX + emailKey, properties.emailAttempts(), properties.emailWindow())) {
                return Optional.of(properties.emailWindow());
            }
            return Optional.empty();
        } catch (RuntimeException unavailable) {
            log.warn("login rate limiter unavailable, letting the attempt through: {}", unavailable.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static String normalize(String raw) {
        if (raw == null || raw.isBlank()) return "-";
        try {
            return Email.of(raw).value();
        } catch (UserAccountException malformed) {
            String lowered = raw.trim().toLowerCase(Locale.ROOT);
            return lowered.length() > Email.MAX_LENGTH ? lowered.substring(0, Email.MAX_LENGTH) : lowered;
        }
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
