package vn.t3nexus.lib.audit;

import vn.t3nexus.lib.events.SensitiveFieldPolicy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chính sách lọc và chuẩn hóa trường dữ kiện bổ sung (details) của sự kiện kiểm toán.
 * <br>Bảo đảm cấu trúc phẳng, không chứa trường nhạy cảm và không vượt giới hạn kích thước.
 */
public final class AuditDetailsPolicy {

    private static final SensitiveFieldPolicy SENSITIVE_POLICY = SensitiveFieldPolicy.strict();

    private AuditDetailsPolicy() {
    }

    public enum Reason {
        FORBIDDEN_NAME,
        BAD_KEY,
        NOT_FLAT,
        TOO_LONG,
        TOO_MANY
    }

    public record Dropped(String key, Reason reason) {
        public Dropped {
            if (key != null && key.length() > 60) {
                key = key.substring(0, 60);
            }
        }
    }

    public record Result(Map<String, Object> kept, List<Dropped> dropped) {
        public Result {
            kept = Collections.unmodifiableMap(new LinkedHashMap<>(kept));
            dropped = List.copyOf(dropped);
        }
    }

    public static Result lenient(Map<?, ?> raw) {
        if (raw == null || raw.isEmpty()) {
            return new Result(Map.of(), List.of());
        }

        List<Map.Entry<?, ?>> entries = new ArrayList<>(raw.entrySet());
        entries.sort(Comparator.comparing(e -> String.valueOf(e.getKey())));

        Map<String, Object> validCandidates = new LinkedHashMap<>();
        List<Dropped> droppedList = new ArrayList<>();

        for (Map.Entry<?, ?> entry : entries) {
            Object rawKey = entry.getKey();
            Object val = entry.getValue();

            if (val == null) {
                // Giá trị null bị bỏ im lặng, không đưa vào dropped
                continue;
            }

            String keyStr = String.valueOf(rawKey);

            // (1) Khóa không phải String hoặc không thỏa AuditContract.isDetailKey
            if (!(rawKey instanceof String sKey) || !AuditContract.isDetailKey(sKey)) {
                droppedList.add(new Dropped(keyStr, Reason.BAD_KEY));
                continue;
            }

            // (2) Khóa nằm trong danh mục cấm
            if (SENSITIVE_POLICY.isForbidden(sKey)) {
                droppedList.add(new Dropped(keyStr, Reason.FORBIDDEN_NAME));
                continue;
            }

            // (3) Giá trị không phải kiểu phẳng được phép
            if (!isFlatAllowed(val)) {
                droppedList.add(new Dropped(keyStr, Reason.NOT_FLAT));
                continue;
            }

            // (4) Chuỗi quá dài
            if (val instanceof String sVal && sVal.length() > AuditContract.MAX_DETAIL_VALUE_LENGTH) {
                droppedList.add(new Dropped(keyStr, Reason.TOO_LONG));
                continue;
            }

            validCandidates.put(sKey, val);
        }

        Map<String, Object> keptMap = new LinkedHashMap<>();
        int count = 0;
        for (Map.Entry<String, Object> entry : validCandidates.entrySet()) {
            if (count < AuditContract.MAX_DETAIL_KEYS) {
                keptMap.put(entry.getKey(), entry.getValue());
                count++;
            } else {
                droppedList.add(new Dropped(entry.getKey(), Reason.TOO_MANY));
            }
        }

        return new Result(keptMap, droppedList);
    }

    public static Map<String, Object> strict(Map<?, ?> raw) {
        Result result = lenient(raw);
        if (!result.dropped().isEmpty()) {
            Dropped first = result.dropped().get(0);
            throw new InvalidAuditDetailsException("Dữ kiện bổ sung không hợp lệ: " + first.key() + " (" + first.reason() + ")");
        }
        return result.kept();
    }

    private static boolean isFlatAllowed(Object val) {
        if (val instanceof String || val instanceof Boolean || val instanceof Byte
                || val instanceof Short || val instanceof Integer || val instanceof Long
                || val instanceof BigInteger || val instanceof BigDecimal) {
            return true;
        }
        if (val instanceof Double d) {
            return Double.isFinite(d);
        }
        if (val instanceof Float f) {
            return Float.isFinite(f);
        }
        return false;
    }
}
