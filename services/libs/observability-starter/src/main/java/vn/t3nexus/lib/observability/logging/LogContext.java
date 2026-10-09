package vn.t3nexus.lib.observability.logging;

import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

/**
 * Ngữ cảnh gắn vào mọi dòng log của một yêu cầu (đặt trong MDC của SLF4J). Xem {@code docs/global/4.convention/logging.md}.
 * <ul>
 *   <li>{@link #put}: khóa nghiệp vụ tra cứu, ra JSON dưới {@code labels.<khóa>} (ví dụ {@code labels.orderId}).</li>
 *   <li>{@link #user}: mã tài khoản, ra {@code user.id}. {@link #operation}: tên thao tác, ra {@code labels.operation}.</li>
 *   <li>{@link #scope}, {@link #wrap}: khôi phục và truyền ngữ cảnh sang luồng khác.</li>
 * </ul>
 * Ghi log không được làm hỏng nghiệp vụ nên các hàm {@code put} không ném lỗi: khóa sai tên hoặc vượt giới hạn số khóa bị bỏ qua.
 */
public final class LogContext {

    public static final String LABEL_PREFIX = "labels.";
    public static final String USER_ID = "user.id";
    public static final String OPERATION = "operation";

    /** Số khóa {@code labels.*} tối đa trong một ngữ cảnh. */
    public static final int MAX_LABELS = 10;
    /** Độ dài giá trị tối đa; dài hơn thì cắt. */
    public static final int MAX_VALUE_LENGTH = 200;

    private static final Pattern KEY = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,39}");

    private LogContext() {
    }

    /**
     * Đặt khóa nghiệp vụ, ví dụ {@code put("orderId", id)}. Giá trị null thì gỡ khóa. Khóa mới vượt {@link #MAX_LABELS} hoặc sai tên bị bỏ qua.
     *
     * @return {@code true} nếu khóa được đặt hoặc gỡ
     */
    public static boolean put(String key, Object value) {
        if (key == null || !KEY.matcher(key).matches()) return false;
        String mdcKey = LABEL_PREFIX + key;
        if (value == null) {
            MDC.remove(mdcKey);
            return true;
        }
        if (MDC.get(mdcKey) == null && countLabels() >= MAX_LABELS) return false;
        MDC.put(mdcKey, clean(String.valueOf(value)));
        return true;
    }

    /** Đặt mã tài khoản của người dùng đang thao tác (trường {@code user.id}). */
    public static void user(String userAccountId) {
        if (userAccountId == null) {
            MDC.remove(USER_ID);
        } else {
            MDC.put(USER_ID, clean(userAccountId));
        }
    }

    /** Đặt tên thao tác (trường {@code labels.operation}); mọi dòng log trong khối sau đó mang tên này. */
    public static boolean operation(String name) {
        if (name == null) {
            MDC.remove(LABEL_PREFIX + OPERATION);
            return true;
        }
        MDC.put(LABEL_PREFIX + OPERATION, clean(name));
        return true;
    }

    public static void remove(String key) {
        if (key != null) MDC.remove(LABEL_PREFIX + key);
    }

    /** Gỡ các khóa do {@code LogContext} đặt ({@code labels.*}, {@code user.id}); không đụng tới ngữ cảnh trace. */
    public static void clear() {
        Map<String, String> current = MDC.getCopyOfContextMap();
        if (current == null) return;
        for (String key : current.keySet()) {
            if (key.startsWith(LABEL_PREFIX) || key.equals(USER_ID)) MDC.remove(key);
        }
    }

    /**
     * Mở một phạm vi: khi đóng, MDC được khôi phục đúng trạng thái lúc mở (không chỉ xóa), nên các phạm vi lồng nhau trong cùng luồng
     * không xóa nhầm khóa của lớp ngoài.
     */
    public static Scope scope() {
        return new Scope(MDC.getCopyOfContextMap());
    }

    /** Bản chụp ngữ cảnh hiện tại của luồng, dùng với {@link #wrap}. */
    public static Map<String, String> snapshot() {
        return MDC.getCopyOfContextMap();
    }

    /**
     * Bọc tác vụ để chạy ở luồng khác với ngữ cảnh của luồng gọi: chụp bản sao lúc gọi hàm này, nạp vào MDC của luồng chạy, và khôi phục
     * trạng thái trước đó khi xong (luồng trong pool không giữ lại gì). Nhánh con thêm khóa riêng không ảnh hưởng luồng cha.
     */
    public static Runnable wrap(Runnable task) {
        Map<String, String> captured = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            apply(captured);
            try {
                task.run();
            } finally {
                apply(previous);
            }
        };
    }

    public static <V> Callable<V> wrap(Callable<V> task) {
        Map<String, String> captured = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            apply(captured);
            try {
                return task.call();
            } finally {
                apply(previous);
            }
        };
    }

    private static void apply(Map<String, String> context) {
        if (context == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }

    private static int countLabels() {
        Map<String, String> current = MDC.getCopyOfContextMap();
        if (current == null) return 0;
        int count = 0;
        for (String key : current.keySet()) {
            if (key.startsWith(LABEL_PREFIX)) count++;
        }
        return count;
    }

    /** Bỏ ký tự điều khiển (xuống dòng, tab...) để không chèn được dòng log giả; cắt giá trị dài. */
    static String clean(String value) {
        StringBuilder sb = new StringBuilder(Math.min(value.length(), MAX_VALUE_LENGTH + 40));
        for (int i = 0; i < value.length() && sb.length() < MAX_VALUE_LENGTH; i++) {
            char c = value.charAt(i);
            sb.append(Character.isISOControl(c) ? ' ' : c);
        }
        if (value.length() > MAX_VALUE_LENGTH) {
            sb.append("…(cắt, tổng ").append(value.length()).append(" ký tự)");
        }
        return sb.toString();
    }

    /** Phạm vi ngữ cảnh; dùng với try-with-resources. */
    public static final class Scope implements AutoCloseable {
        private final Map<String, String> saved;

        private Scope(Map<String, String> saved) {
            this.saved = saved;
        }

        @Override
        public void close() {
            apply(saved);
        }
    }
}
