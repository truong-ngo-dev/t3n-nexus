package vn.t3nexus.lib.audit;

import java.util.regex.Pattern;

/**
 * Hợp đồng hằng số và các hàm kiểm tra tính hợp lệ của trường kiểm toán.
 * <br>Lớp tiện ích không lưu trạng thái để dùng chung giữa bên ghi và bên đọc.
 */
public final class AuditContract {

    public static final String TOPIC = "audit.action.recorded";
    public static final String EVENT_TYPE = "AuditActionRecorded";
    public static final String SYSTEM_ROLE = "SYSTEM";
    public static final int MAX_DETAIL_KEYS = 10;
    public static final int MAX_DETAIL_VALUE_LENGTH = 500;
    public static final int MAX_ID_LENGTH = 100;

    private static final Pattern ACTION_PATTERN = Pattern.compile("^[A-Z][A-Z0-9]*(_[A-Z0-9]+)+$");
    private static final Pattern ROLE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{0,31}$");
    private static final Pattern TARGET_TYPE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{0,63}$");
    private static final Pattern SOURCE_SERVICE_PATTERN = Pattern.compile("^[a-z0-9-]{1,64}$");
    private static final Pattern DETAIL_KEY_PATTERN = Pattern.compile("^[a-z][A-Za-z0-9]{0,39}$");

    private AuditContract() {
    }

    public static boolean isAction(String action) {
        if (action == null || action.length() > 100) {
            return false;
        }
        return ACTION_PATTERN.matcher(action).matches();
    }

    public static boolean isRole(String role) {
        if (role == null) {
            return false;
        }
        return ROLE_PATTERN.matcher(role).matches();
    }

    public static boolean isTargetType(String targetType) {
        if (targetType == null) {
            return false;
        }
        return TARGET_TYPE_PATTERN.matcher(targetType).matches();
    }

    public static boolean isSourceService(String sourceService) {
        if (sourceService == null) {
            return false;
        }
        return SOURCE_SERVICE_PATTERN.matcher(sourceService).matches();
    }

    public static boolean isDetailKey(String detailKey) {
        if (detailKey == null) {
            return false;
        }
        return DETAIL_KEY_PATTERN.matcher(detailKey).matches();
    }

    public static boolean isTargetId(String targetId) {
        if (targetId == null || targetId.isBlank() || targetId.length() > MAX_ID_LENGTH) {
            return false;
        }
        return true;
    }

    public static boolean isEventId(String eventId) {
        if (eventId == null || eventId.isBlank() || eventId.length() > MAX_ID_LENGTH) {
            return false;
        }
        return true;
    }
}
