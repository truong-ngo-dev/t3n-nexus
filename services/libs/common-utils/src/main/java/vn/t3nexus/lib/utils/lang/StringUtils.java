package vn.t3nexus.lib.utils.lang;

import java.text.Normalizer;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * General-purpose String utilities — null-safe by default.
 * <p>
 * Methods return {@code null}, empty, or {@code false} on null input unless the contract
 * explicitly states otherwise (e.g. {@link #toChar(String)} throws on null).
 * </p>
 *
 * @author Truong Ngo
 * @version 1.0.0
 * @since 1.0.0
 */
public final class StringUtils {

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * CONSTANTS
     * -----------------------------------------------------------------------------------------------------------------
     */

    public static final String EMPTY = "";

    private static final String EMAIL_REGEX =
            "[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*" +
            "@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?";

    private static final String ALPHANUMERIC_REGEX = "[a-zA-Z0-9]+";

    private static final String URL_REGEX_PREFIX = "^(?i)(%s)://[\\w\\-]+(\\.[\\w\\-]+)+[/#?]?.*$";

    private static final Pattern EMAIL_PATTERN         = Pattern.compile(EMAIL_REGEX, Pattern.CASE_INSENSITIVE);
    private static final Pattern ALPHANUMERIC_PATTERN  = Pattern.compile(ALPHANUMERIC_REGEX);
    private static final Pattern ALL_WHITESPACE        = Pattern.compile("\\s");
    private static final Pattern DIACRITICS            = Pattern.compile("\\p{InCombiningDiacriticalMarks}");
    private static final Pattern SLUG_UNSAFE           = Pattern.compile("[^\\w\\s-]");
    private static final Pattern SLUG_SPACES           = Pattern.compile("[\\s_]+");
    private static final Pattern SLUG_MULTI_HYPHEN     = Pattern.compile("-{2,}");
    private static final Pattern PATH_VARIABLE         = Pattern.compile("\\{[^}]+}");
    private static final Pattern PATH_VARIABLE_W_GROUP = Pattern.compile("\\{([^}]+)}");

    private StringUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * NULL / EMPTY CHECKS
     * -----------------------------------------------------------------------------------------------------------------
     */

    /** Returns {@code true} if {@code s} is null or {@code ""}. */
    public static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    /** Returns {@code true} if {@code s} is non-null and non-empty. */
    public static boolean isNotEmpty(String s) {
        return !isEmpty(s);
    }

    /** Returns {@code true} if {@code s} is null, empty, or contains only whitespace. */
    public static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Returns {@code true} if {@code s} is non-null and contains at least one non-whitespace character. */
    public static boolean isNotBlank(String s) {
        return !isBlank(s);
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * DEFAULTS
     * -----------------------------------------------------------------------------------------------------------------
     */

    /** Returns {@code ""} if {@code s} is null, otherwise returns {@code s}. */
    public static String nullToEmpty(String s) {
        return s == null ? EMPTY : s;
    }

    /** Returns {@code null} if {@code s} is empty, otherwise returns {@code s}. */
    public static String emptyToNull(String s) {
        return isEmpty(s) ? null : s;
    }

    /** Returns {@code null} if {@code s} is blank, otherwise returns {@code s}. */
    public static String blankToNull(String s) {
        return isBlank(s) ? null : s;
    }

    /** Returns {@code def} if {@code s} is null, otherwise returns {@code s}. */
    public static String defaultIfNull(String s, String def) {
        return s == null ? def : s;
    }

    /** Returns {@code def} if {@code s} is empty, otherwise returns {@code s}. */
    public static String defaultIfEmpty(String s, String def) {
        return isEmpty(s) ? def : s;
    }

    /** Returns {@code def} if {@code s} is blank, otherwise returns {@code s}. */
    public static String defaultIfBlank(String s, String def) {
        return isBlank(s) ? def : s;
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * TRIM
     * -----------------------------------------------------------------------------------------------------------------
     */

    /** Null-safe {@link String#trim()}. */
    public static String trim(String s) {
        return s == null ? null : s.trim();
    }

    /**
     * Strips leading characters contained in {@code stripChars} from {@code s}.
     * Strips whitespace if {@code stripChars} is {@code null}.
     *
     * <pre>{@code
     * stripStart("yxabc", "xyz") = "abc"
     * stripStart("  abc", null)  = "abc"
     * }</pre>
     */
    public static String stripStart(String s, String stripChars) {
        if (isEmpty(s)) return s;
        int start = 0;
        if (stripChars == null) {
            while (start < s.length() && Character.isWhitespace(s.charAt(start))) start++;
        } else {
            while (start < s.length() && stripChars.indexOf(s.charAt(start)) != -1) start++;
        }
        return s.substring(start);
    }

    /**
     * Strips trailing characters contained in {@code stripChars} from {@code s}.
     * Strips whitespace if {@code stripChars} is {@code null}.
     *
     * <pre>{@code
     * stripEnd("abcyx", "xyz") = "abc"
     * stripEnd("abc  ", null)  = "abc"
     * }</pre>
     */
    public static String stripEnd(String s, String stripChars) {
        if (isEmpty(s)) return s;
        int end = s.length();
        if (stripChars == null) {
            while (end > 0 && Character.isWhitespace(s.charAt(end - 1))) end--;
        } else {
            while (end > 0 && stripChars.indexOf(s.charAt(end - 1)) != -1) end--;
        }
        return s.substring(0, end);
    }

    /** Trims {@code s}; returns {@code null} if result is empty. */
    public static String trimToNull(String s) {
        String t = trim(s);
        return isEmpty(t) ? null : t;
    }

    /** Trims {@code s}; returns {@code ""} if input is null. */
    public static String trimToEmpty(String s) {
        String t = trim(s);
        return t == null ? EMPTY : t;
    }

    /** Removes ALL whitespace characters including internal spaces. */
    public static String trimAll(String s) {
        return s == null ? null : ALL_WHITESPACE.matcher(s).replaceAll(EMPTY);
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * COMPARISON (null-safe)
     * -----------------------------------------------------------------------------------------------------------------
     */

    /** Null-safe string equality. */
    public static boolean equals(String s1, String s2) {
        return Objects.equals(s1, s2);
    }

    /** Null-safe case-insensitive equality. */
    public static boolean equalsIgnoreCase(String s1, String s2) {
        return s1 == null ? s2 == null : s1.equalsIgnoreCase(s2);
    }

    /** Null-safe {@link String#contains(CharSequence)}. */
    public static boolean contains(String s, String sub) {
        return s != null && sub != null && s.contains(sub);
    }

    /** Null-safe case-insensitive contains. */
    public static boolean containsIgnoreCase(String s, String sub) {
        return s != null && sub != null && s.toLowerCase().contains(sub.toLowerCase());
    }

    /** Null-safe {@link String#startsWith(String)}. */
    public static boolean startsWith(String s, String prefix) {
        return s != null && prefix != null && s.startsWith(prefix);
    }

    /** Null-safe {@link String#endsWith(String)}. */
    public static boolean endsWith(String s, String suffix) {
        return s != null && suffix != null && s.endsWith(suffix);
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * CASE
     * -----------------------------------------------------------------------------------------------------------------
     */

    /** Null-safe {@link String#toUpperCase()}. */
    public static String upperCase(String s) {
        return s == null ? null : s.toUpperCase();
    }

    /** Null-safe {@link String#toLowerCase()}. */
    public static String lowerCase(String s) {
        return s == null ? null : s.toLowerCase();
    }

    /** Uppercases the first character; leaves the rest unchanged. */
    public static String capitalize(String s) {
        if (isEmpty(s)) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Lowercases the first character; leaves the rest unchanged. */
    public static String uncapitalize(String s) {
        if (isEmpty(s)) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * NAMING CONVENTIONS
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Converts a string to {@code snake_case}.
     * Handles camelCase, PascalCase, kebab-case, and space-separated inputs.
     */
    public static String toSnakeCase(String s) {
        if (isBlank(s)) return s;
        return s.trim()
                .replaceAll("([a-z])([A-Z])", "$1_$2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
                .replaceAll("[\\s\\-]+", "_")
                .toLowerCase();
    }

    /**
     * Converts a string to {@code camelCase}.
     * Handles snake_case, kebab-case, and space-separated inputs.
     */
    public static String toCamelCase(String s) {
        if (isBlank(s)) return s;
        String[] parts = s.trim().split("[_\\-\\s]+");
        StringBuilder sb = new StringBuilder(lowerCase(parts[0]));
        for (int i = 1; i < parts.length; i++) {
            sb.append(capitalize(lowerCase(parts[i])));
        }
        return sb.toString();
    }

    /**
     * Converts a string to {@code kebab-case}.
     * Handles camelCase, PascalCase, snake_case, and space-separated inputs.
     */
    public static String toKebabCase(String s) {
        return isBlank(s) ? s : toSnakeCase(s).replace('_', '-');
    }

    /**
     * Converts a string to {@code PascalCase}.
     * Handles snake_case, kebab-case, and space-separated inputs.
     */
    public static String toPascalCase(String s) {
        return isBlank(s) ? s : capitalize(toCamelCase(s));
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * MANIPULATION
     * -----------------------------------------------------------------------------------------------------------------
     */

    /** Repeats {@code s} exactly {@code times} times; returns {@code ""} if {@code times <= 0}. */
    public static String repeat(String s, int times) {
        if (s == null) return null;
        return times <= 0 ? EMPTY : s.repeat(times);
    }

    /** Reverses {@code s}. */
    public static String reverse(String s) {
        return s == null ? null : new StringBuilder(s).reverse().toString();
    }

    /** Truncates {@code s} to {@code maxWidth} characters, appending {@code "..."} if truncated. */
    public static String abbreviate(String s, int maxWidth) {
        return abbreviate(s, maxWidth, "...");
    }

    /** Truncates {@code s} to {@code maxWidth} characters, appending {@code ellipsis} if truncated. */
    public static String abbreviate(String s, int maxWidth, String ellipsis) {
        if (s == null) return null;
        if (s.length() <= maxWidth) return s;
        String e = nullToEmpty(ellipsis);
        if (maxWidth <= e.length()) return s.substring(0, maxWidth);
        return s.substring(0, maxWidth - e.length()) + e;
    }

    /** Left-pads {@code s} with {@code padChar} until the total length reaches {@code size}. */
    public static String leftPad(String s, int size, char padChar) {
        if (s == null) return null;
        int pads = size - s.length();
        return pads <= 0 ? s : repeat(String.valueOf(padChar), pads) + s;
    }

    /** Right-pads {@code s} with {@code padChar} until the total length reaches {@code size}. */
    public static String rightPad(String s, int size, char padChar) {
        if (s == null) return null;
        int pads = size - s.length();
        return pads <= 0 ? s : s + repeat(String.valueOf(padChar), pads);
    }

    /** Centers {@code s} in a field of {@code size}, padding both sides with {@code padChar}. */
    public static String center(String s, int size, char padChar) {
        if (s == null) return null;
        int pads = size - s.length();
        if (pads <= 0) return s;
        int left = pads / 2;
        return repeat(String.valueOf(padChar), left) + s + repeat(String.valueOf(padChar), pads - left);
    }

    /** Wraps {@code s} with the given {@code prefix} and {@code suffix}. */
    public static String wrap(String s, String prefix, String suffix) {
        return s == null ? null : nullToEmpty(prefix) + s + nullToEmpty(suffix);
    }

    /** Replaces whitespace sequences in {@code s} with {@code replacement} after trimming. */
    public static String replaceWhitespace(String s, String replacement) {
        return s == null ? null : s.trim().replaceAll("\\s+", replacement);
    }

    /** Counts non-overlapping occurrences of {@code sub} in {@code s}. */
    public static int countOccurrences(String s, String sub) {
        if (isEmpty(s) || isEmpty(sub)) return 0;
        int count = 0;
        int idx = 0;
        while ((idx = s.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * SUBSTRING
     * -----------------------------------------------------------------------------------------------------------------
     */

    /** Returns the substring before the first occurrence of {@code sep}; returns {@code s} if not found. */
    public static String substringBefore(String s, String sep) {
        if (isEmpty(s) || sep == null) return s;
        int idx = s.indexOf(sep);
        return idx == -1 ? s : s.substring(0, idx);
    }

    /** Returns the substring after the first occurrence of {@code sep}; returns {@code ""} if not found. */
    public static String substringAfter(String s, String sep) {
        if (isEmpty(s) || sep == null) return s;
        int idx = s.indexOf(sep);
        return idx == -1 ? EMPTY : s.substring(idx + sep.length());
    }

    /** Returns the substring between the first occurrence of {@code open} and {@code close}; {@code null} if not found. */
    public static String substringBetween(String s, String open, String close) {
        if (s == null || open == null || close == null) return null;
        int start = s.indexOf(open);
        if (start == -1) return null;
        int end = s.indexOf(close, start + open.length());
        return end == -1 ? null : s.substring(start + open.length(), end);
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * JOIN / SPLIT
     * -----------------------------------------------------------------------------------------------------------------
     */

    /** Joins {@code list} elements via {@code mapper} with {@code delimiter}; returns {@code null} if list is empty. */
    public static <T> String join(List<T> list, String delimiter, Function<T, String> mapper) {
        if (list == null || list.isEmpty()) return null;
        return list.stream().map(mapper).collect(Collectors.joining(delimiter));
    }

    /** Joins {@code collection} elements via {@link Object#toString()} with {@code delimiter}; returns {@code null} if empty. */
    public static String join(Collection<?> collection, String delimiter) {
        if (collection == null || collection.isEmpty()) return null;
        return collection.stream()
                .map(o -> o == null ? EMPTY : o.toString())
                .collect(Collectors.joining(delimiter));
    }

    /** Splits {@code s} by {@code delimiter} and maps each token via {@code mapper}. */
    public static <T> List<T> split(String s, String delimiter, Function<String, T> mapper) {
        if (isBlank(s)) return Collections.emptyList();
        return Stream.of(s.split(delimiter)).map(mapper).collect(Collectors.toList());
    }

    /** Splits {@code s} by {@code delimiter} into a {@code List<String>}. */
    public static List<String> split(String s, String delimiter) {
        return split(s, delimiter, Function.identity());
    }

    /**
     * Tách chuỗi theo ký tự phân cách và đảm bảo trả về mảng có độ dài cố định.
     * * @param input     Chuỗi đầu vào (vd: "sp1/sp2")
     * @param delimiter Ký tự phân cách (vd: "/")
     * @param size      Số lượng level mong muốn (vd: 5)
     * @return Mảng String luôn có độ dài bằng size
     */
    public static String[] splitToLevels(String input, String delimiter, int size) {
        String[] result = new String[size];
        if (input == null || input.isEmpty()) {
            return result; // Trả về mảng chứa toàn null
        }

        // Sử dụng limit = -1 để không bỏ qua các phần tử rỗng ở cuối
        String[] parts = input.split(Pattern.quote(delimiter), -1);

        for (int i = 0; i < size; i++) {
            if (i < parts.length) {
                result[i] = Optional.ofNullable(parts[i]).orElse(EMPTY);
            } else {
                result[i] = null; // Hoặc "" tùy theo yêu cầu logic của bạn
            }
        }
        return result;
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * VALIDATION
     * -----------------------------------------------------------------------------------------------------------------
     */

    /** Returns {@code true} if {@code s} is a valid email address. */
    public static boolean isEmail(String s) {
        return isNotBlank(s) && EMAIL_PATTERN.matcher(s).matches();
    }

    /** Returns {@code true} if {@code s} contains only Unicode letters. */
    public static boolean isAlpha(String s) {
        return isNotBlank(s) && s.chars().allMatch(Character::isLetter);
    }

    /** Returns {@code true} if {@code s} contains only ASCII letters and digits. */
    public static boolean isAlphanumeric(String s) {
        return isNotBlank(s) && ALPHANUMERIC_PATTERN.matcher(s).matches();
    }

    /** Returns {@code true} if {@code s} contains only digit characters (0–9). */
    public static boolean isNumeric(String s) {
        return isNotBlank(s) && s.chars().allMatch(Character::isDigit);
    }

    /** Returns {@code true} if {@code s} is a URL matching one of the given {@code schemes}. */
    public static boolean isUrl(String s, List<String> schemes) {
        if (isBlank(s) || schemes == null || schemes.isEmpty()) return false;
        return s.matches(String.format(URL_REGEX_PREFIX, String.join("|", schemes)));
    }

    /** Returns {@code true} if {@code s} is blank, {@code "false"}, {@code "null"}, or {@code "undefined"}. */
    public static boolean isFalsy(String s) {
        return isBlank(s) || "false".equals(s) || "null".equals(s) || "undefined".equals(s);
    }

    /** Returns {@code true} if {@code s} is exactly one character long. */
    public static boolean isCharacter(String s) {
        return s != null && s.length() == 1;
    }

    /**
     * Converts {@code s} to its single {@code char} value.
     *
     * @throws IllegalArgumentException if {@code s} is null or not exactly one character
     */
    public static char toChar(String s) {
        if (s == null) throw new IllegalArgumentException("Cannot convert null to char");
        if (!isCharacter(s)) throw new IllegalArgumentException("Invalid character: " + s);
        return s.charAt(0);
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * SENSITIVE DATA
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Masks characters between index {@code start} (inclusive) and {@code end} (exclusive) with {@code maskChar}.
     *
     * <pre>{@code mask("0912345678", 3, 7, '*') == "091****678"}</pre>
     */
    public static String mask(String s, int start, int end, char maskChar) {
        if (s == null) return null;
        int from = Math.max(0, start);
        int to = Math.min(s.length(), end);
        if (from >= to) return s;
        char[] chars = s.toCharArray();
        for (int i = from; i < to; i++) chars[i] = maskChar;
        return new String(chars);
    }

    /**
     * Masks the local part of an email address, keeping only the first character.
     *
     * <pre>{@code maskEmail("user@example.com") == "u***@example.com"}</pre>
     */
    public static String maskEmail(String email) {
        if (isBlank(email)) return email;
        int atIdx = email.indexOf('@');
        if (atIdx <= 1) return email;
        return email.charAt(0) + repeat("*", atIdx - 1) + email.substring(atIdx);
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * ACCENT / SLUG
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Removes diacritical marks (accents) from {@code s} using Unicode NFD decomposition.
     * Handles Vietnamese {@code đ/Đ} explicitly as NFD cannot decompose them.
     */
    public static String removeAccent(String s) {
        if (s == null) return null;
        return DIACRITICS.matcher(Normalizer.normalize(s, Normalizer.Form.NFD))
                .replaceAll(EMPTY)
                .replace("đ", "d")
                .replace("Đ", "D");
    }

    /**
     * Converts {@code s} to a URL-safe slug.
     *
     * <pre>{@code toSlug("Xin Chào Thế Giới!") == "xin-chao-the-gioi"}</pre>
     */
    public static String toSlug(String s) {
        if (isBlank(s)) return s;
        String base = removeAccent(s.trim().toLowerCase());
        String withoutUnsafe = SLUG_UNSAFE.matcher(base).replaceAll(EMPTY);
        String hyphenated = SLUG_SPACES.matcher(withoutUnsafe).replaceAll("-");
        return SLUG_MULTI_HYPHEN.matcher(hyphenated).replaceAll("-")
                .replaceAll("^-|-$", "");
    }
}
