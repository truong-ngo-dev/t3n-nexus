package vn.t3nexus.lib.observability.http;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import vn.t3nexus.lib.observability.logging.SecretPatterns;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Làm sạch nội dung yêu cầu trước khi ghi log (xem {@code docs/global/4.convention/logging.md} mục 6). Lớp thuần, không phụ thuộc Spring.
 * <ol>
 *   <li>Chỉ JSON hoặc chữ; loại khác chỉ ghi loại và kích thước.</li>
 *   <li>Che trường có tên nhạy cảm ở mọi độ sâu, không phân biệt hoa thường.</li>
 *   <li>Danh sách trường được phép (cấp cao nhất): trường ngoài danh sách bị bỏ hẳn.</li>
 *   <li>Quét mẫu bí mật trong giá trị chuỗi; cắt giá trị dài và mảng dài; chuỗi dài giống base64 chỉ ghi kích thước.</li>
 *   <li>Đóng gói thành một chuỗi.</li>
 * </ol>
 * Không bao giờ ném lỗi: lỗi bên trong trả về một chuỗi báo "không ghi được nội dung".
 */
public class BodySanitizer {

    public static final String MASK = SecretPatterns.MASK;
    public static final String UNREADABLE = "[không ghi được nội dung]";

    /** Chuỗi chỉ gồm ký tự của base64 hoặc base64url: nhiều khả năng là dữ liệu nhị phân mã hóa (ảnh, tệp) nên không đáng ghi nguyên văn. */
    private static final Pattern BASE64_LIKE = Pattern.compile("[A-Za-z0-9+/=_-]+");

    /** Ngưỡng và danh sách tên nhạy cảm đang áp dụng. */
    private final Options options;
    /** Đọc và ghi JSON; dựng cây kết quả mới thay vì sửa cây gốc. */
    private final JsonMapper mapper = JsonMapper.builder().build();
    /** Mẫu dựng một lần từ danh sách tên nhạy cảm, dùng cho nhánh chữ ({ #maskNamedValues}). */
    private final Pattern namedValuePattern;

    /** Dùng ngưỡng và danh sách tên nhạy cảm của {@code options}. */
    public BodySanitizer(Options options) {
        this.options = options;
        this.namedValuePattern = buildNamedValuePattern(options);
    }

    /**
     * Dựng mẫu tìm cặp {@code tên: giá trị} hoặc {@code tên=giá trị} có tên nhạy cảm: tên chứa một mảnh trong {@code maskContains}
     * (tối đa 40 ký tự đứng trước mảnh, tiếp theo tối đa 40 ký tự định danh), hoặc trùng đúng một tên trong {@code maskExact}. Không phân biệt hoa thường.
     * Chỉ dùng cho nội dung không phải JSON hợp lệ, nên mẫu cố ý rộng tay: che nhầm còn hơn lọt.
     */
    private static Pattern buildNamedValuePattern(Options options) {
        StringBuilder names = new StringBuilder();
        for (String part : options.maskContains()) {
            if (!names.isEmpty()) names.append('|');
            names.append(".{0,40}?").append(Pattern.quote(part));
        }
        for (String exact : options.maskExact()) {
            if (!names.isEmpty()) names.append('|');
            names.append(Pattern.quote(exact));
        }
        // nhóm 1: tên trường (có thể có dấu nháy) kèm dấu : hoặc =; nhóm 2: giá trị trong nháy (kể cả chưa đóng nháy) hoặc giá trị trần
        return Pattern.compile("(?i)((?:\"|\\b)(?:" + names + ")[A-Za-z0-9_]{0,40}\"?\\s*[:=]\\s*)(\"[^\"]*\"?|[^,}\\]\\s\"]+)");
    }

    /** Dùng ngưỡng mặc định ({@link Options#defaults()}). */
    public BodySanitizer() {
        this(Options.defaults());
    }

    /**
     * @param body        các byte đã đệm (tối đa {@link Options#maxBytes()})
     * @param contentType giá trị tiêu đề {@code Content-Type}, có thể null
     * @param allowFields trường cấp cao nhất được phép ghi; null hoặc rỗng nghĩa là không giới hạn (chỉ che theo tên)
     * @return chuỗi đã làm sạch, hoặc null nếu không có nội dung
     */
    public String sanitize(byte[] body, String contentType, Set<String> allowFields) {
        try {
            if (body == null || body.length == 0) return null;
            if (!isJson(contentType) && !isText(contentType)) {
                return "[nội dung không ghi: " + (contentType == null ? "không rõ loại" : contentType) + ", " + body.length + " byte]";
            }
            String text = new String(body, StandardCharsets.UTF_8);
            if (isJson(contentType) || looksLikeJson(text)) {
                try {
                    JsonNode tree = mapper.readTree(text);
                    JsonNode cleaned = clean(tree, 0, allowFields);
                    return mapper.writeValueAsString(cleaned);
                } catch (RuntimeException notParseable) {
                    // JSON hỏng hoặc bị cắt: rơi xuống nhánh chữ, vẫn quét bí mật
                }
            }
            // Nội dung không phân tích được: danh sách trường được phép không thể áp dụng, nên không ghi gì để không lộ trường ngoài danh sách.
            if (allowFields != null && !allowFields.isEmpty()) {
                return "[nội dung không phân tích được: " + body.length + " byte]";
            }
            return cleanText(text, body.length);
        } catch (RuntimeException e) {
            return UNREADABLE;
        }
    }

    /**
     * Làm sạch một nút của cây JSON, trả về nút mới.
     * <ul>
     *   <li>Đối tượng: ở cấp cao nhất ({@code depth == 0}) bỏ hẳn trường ngoài danh sách được phép; trường có tên nhạy cảm (ở mọi độ sâu) thay giá trị bằng {@link #MASK};
     *       trường còn lại làm sạch đệ quy.</li>
     *   <li>Mảng: giữ tối đa {@code maxArrayItems} phần tử đầu, thêm một phần tử chữ cho biết số phần tử bị bỏ.</li>
     *   <li>Chuỗi: xử lý qua {@link #cleanValue}.</li>
     *   <li>Số, true/false, null: giữ nguyên.</li>
     * </ul>
     * Danh sách trường được phép chỉ áp dụng ở cấp cao nhất, nên lời gọi đệ quy truyền {@code null}.
     */
    private JsonNode clean(JsonNode node, int depth, Set<String> allowFields) {
        if (node.isObject()) {
            ObjectNode out = mapper.createObjectNode();
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                String name = field.getKey();
                if (depth == 0 && allowFields != null && !allowFields.isEmpty() && !allowFields.contains(name)) continue;
                if (isSensitiveName(name)) {
                    out.put(name, MASK);
                } else {
                    out.set(name, clean(field.getValue(), depth + 1, null));
                }
            }
            return out;
        }
        if (node.isArray()) {
            ArrayNode out = mapper.createArrayNode();
            int kept = 0;
            for (JsonNode item : node) {
                if (kept == options.maxArrayItems()) break;
                out.add(clean(item, depth + 1, null));
                kept++;
            }
            if (node.size() > kept) out.add("…(+" + (node.size() - kept) + " phần tử)");
            return out;
        }
        if (node.isString()) {
            return mapper.getNodeFactory().stringNode(cleanValue(node.stringValue()));
        }
        return node;
    }

    /**
     * Làm sạch một giá trị chuỗi. Thứ tự có ý nghĩa: chuỗi dài và giống base64 chỉ ghi độ dài (kiểm trước vì quét mẫu trên chuỗi rất dài vô ích);
     * sau đó che {@code Bearer ...}, JWT, mã băm bằng {@link SecretPatterns#scrub}; cuối cùng cắt nếu vẫn dài hơn {@code maxValueLength}.
     */
    private String cleanValue(String value) {
        int length = value.length();
        if (length >= options.maxValueLength() && BASE64_LIKE.matcher(value).matches()) {
            return "[dữ liệu nhị phân " + length + " ký tự]";
        }
        String scrubbed = SecretPatterns.scrub(value);
        if (scrubbed.length() > options.maxValueLength()) {
            return scrubbed.substring(0, options.maxValueLength()) + "…(cắt, tổng " + length + " ký tự)";
        }
        return scrubbed;
    }

    /**
     * Làm sạch nội dung chữ (không phải JSON, hoặc JSON hỏng mà không có danh sách trường được phép): quét mẫu bí mật, che giá trị của cặp tên nhạy cảm,
     * thay ký tự điều khiển bằng dấu cách (chống giả dòng log), cắt ở {@code maxTextLength}. Nếu nội dung gốc đã chạm trần đệm
     * ({@code maxBytes}) thì báo thêm là đã bị cắt ở khâu đệm.
     *
     * @param byteLength số byte của nội dung gốc, để biết nó có bị cắt ở khâu đệm hay không
     */
    private String cleanText(String text, int byteLength) {
        String scrubbed = maskNamedValues(SecretPatterns.scrub(text)).replaceAll("\\p{Cntrl}", " ");
        String result = scrubbed.length() > options.maxTextLength()
                ? scrubbed.substring(0, options.maxTextLength()) + "…(cắt)"
                : scrubbed;
        return byteLength >= options.maxBytes() ? result + " …(đã cắt ở " + options.maxBytes() + " byte)" : result;
    }

    /**
     * Che giá trị của các cặp {@code tên: giá trị} hoặc {@code tên=giá trị} có tên nhạy cảm trong chuỗi không phải JSON hợp lệ (JSON hỏng hoặc bị cắt),
     * để mật khẩu trong nội dung dở dang cũng không lọt vào log.
     */
    private String maskNamedValues(String text) {
        return namedValuePattern.matcher(text).replaceAll("$1\"" + MASK + "\"");
    }

    /** Tên trường có nhạy cảm không: trùng đúng tên trong {@code maskExact} hoặc chứa một mảnh trong {@code maskContains}; không phân biệt hoa thường. */
    private boolean isSensitiveName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (options.maskExact().contains(lower)) return true;
        for (String part : options.maskContains()) {
            if (lower.contains(part)) return true;
        }
        return false;
    }

    /** Loại nội dung khai báo là JSON (chứa "json", gồm cả {@code application/problem+json}). */
    private static boolean isJson(String contentType) {
        return contentType != null && contentType.toLowerCase(Locale.ROOT).contains("json");
    }

    /** Loại nội dung khai báo là chữ ({@code text/...}). */
    private static boolean isText(String contentType) {
        return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("text/");
    }

    /** Nội dung bắt đầu bằng ngoặc nhọn hoặc ngoặc vuông (sau khoảng trắng đầu): dùng khi loại khai báo là chữ nhưng thân lại là JSON. */
    private static boolean looksLikeJson(String text) {
        String trimmed = text.stripLeading();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    /**
     * @param maxBytes       số byte tối đa đệm từ nội dung yêu cầu
     * @param maxValueLength độ dài tối đa của một giá trị chuỗi
     * @param maxArrayItems  số phần tử tối đa giữ lại của một mảng
     * @param maxTextLength  độ dài tối đa của nội dung không phải JSON
     * @param maskExact      tên trường (chữ thường) che khi khớp đúng
     * @param maskContains   mảnh tên (chữ thường) che khi tên trường chứa nó
     */
    public record Options(int maxBytes, int maxValueLength, int maxArrayItems, int maxTextLength,
                          Set<String> maskExact, List<String> maskContains) {

        public static Options defaults() {
            return new Options(4096, 200, 3, 1000,
                    Set.of("otp", "pin", "pass", "cvc", "cvv"),
                    List.of("password", "passwd", "secret", "token", "authorization", "apikey", "api_key", "credential", "cardnumber"));
        }
    }
}
