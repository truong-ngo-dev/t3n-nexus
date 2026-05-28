/*
 * Created by Truong Ngo (2026).
 */
package vn.t3nexus.lib.utils.lang;

import vn.t3nexus.lib.utils.type.DataTypeUtils;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * High-performance and safe numeric utility framework.
 * <p>
 * This class provides comprehensive utilities for number classification,
 * system identification (Hex, Octal), validation, conversion, and math operations.
 * It handles various number types including primitives, wrappers, {@link BigInteger},
 * and {@link BigDecimal}.
 * </p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 * <li><b>Classification:</b> Check if a type is Integer, Decimal, Floating Point, etc.</li>
 * <li><b>Identification:</b> Detect Hexadecimal, Octal, Scientific Notation strings.</li>
 * <li><b>Validation:</b> Check for Zero, Positive, Valid Number (not NaN/Infinite).</li>
 * <li><b>Conversion:</b> Safe conversion between number types with overflow detection.</li>
 * </ul>
 *
 * <p>This class is thread-safe and intended for static use.</p>
 *
 * @author Truong Ngo
 * @version 1.0.0
 * @since 1.0.0
 */
public final class NumberUtils {

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * CONSTANTS
     * -----------------------------------------------------------------------------------------------------------------
     */

    /** The plus sign symbol. */
    public static final String PLUS = "+";

    /** The minus sign symbol. */
    public static final String MINUS = "-";

    /** The prefix for hexadecimal numbers starting with 0x. */
    public static final String HEX_PREFIX_0X = "0x";

    /** The prefix for hexadecimal numbers starting with #. */
    public static final String HEX_PREFIX_HASH = "#";

    private static final char EXPONENT = 'e';
    private static final char DECIMAL_POINT = '.';

    private static final char[] JAVA_NUMERIC_SUFFIXES = {
            'L', 'l', // Long
            'F', 'f', // Float
            'D', 'd'  // Double
    };

    /**
     * Prevents instantiation of this utility class.
     * @throws UnsupportedOperationException always
     */
    private NumberUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * CLASSIFICATION
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Checks if the given type is an integer type (byte, short, int, long, BigInteger).
     *
     * <pre>{@code
     * NumberUtils.isIntegerType(Integer.class) = true
     * NumberUtils.isIntegerType(Double.class)  = false
     * }</pre>
     *
     * @param type the class to check (maybe {@code null})
     * @return {@code true} if the type is an integer type
     * @since 1.0.0
     */
    public static boolean isIntegerType(Class<?> type) {
        if (!DataTypeUtils.isNumber(type)) return false;
        return type == Integer.class || type == int.class   ||
               type == Long.class    || type == long.class  ||
               type == Short.class   || type == short.class ||
               type == Byte.class    || type == byte.class  ||
               type == BigInteger.class;
    }

    /**
     * Checks if the given type is a decimal type (float, double, BigDecimal).
     *
     * <pre>{@code
     * NumberUtils.isDecimalType(BigDecimal.class) = true
     * NumberUtils.isDecimalType(Integer.class)    = false
     * }</pre>
     *
     * @param type the class to check (maybe {@code null})
     * @return {@code true} if the type is a decimal type
     * @since 1.0.0
     */
    public static boolean isDecimalType(Class<?> type) {
        if (!DataTypeUtils.isNumber(type)) return false;
        return type == Double.class || type == double.class ||
               type == Float.class  || type == float.class  ||
               type == BigDecimal.class;
    }

    /**
     * Checks if the given type supports exact precision (Integer types or BigDecimal).
     *
     * <pre>{@code
     * NumberUtils.isExactPrecision(BigDecimal.class) = true
     * NumberUtils.isExactPrecision(Double.class)     = false
     * }</pre>
     *
     * @param type the class to check (maybe {@code null})
     * @return {@code true} if the type has exact precision
     * @since 1.0.0
     */
    public static boolean isExactPrecision(Class<?> type) {
        return isIntegerType(type) || type == BigDecimal.class;
    }

    /**
     * Checks if the given type is a floating-point type (float, double).
     *
     * <pre>{@code
     * NumberUtils.isFloatingPointType(Double.class) = true
     * }</pre>
     *
     * @param type the class to check (maybe {@code null})
     * @return {@code true} if the type is a floating-point type
     * @since 1.0.0
     */
    public static boolean isFloatingPointType(Class<?> type) {
        return type == Double.class || type == double.class ||
               type == Float.class  || type == float.class;
    }

    /**
     * Checks if the given type is a large number type (BigInteger, BigDecimal).
     *
     * <pre>{@code
     * NumberUtils.isLargeNumber(BigInteger.class) = true
     * }</pre>
     *
     * @param type the class to check (maybe {@code null})
     * @return {@code true} if the type is a large number
     * @since 1.0.0
     */
    public static boolean isLargeNumber(Class<?> type) {
        return type == BigInteger.class || type == BigDecimal.class;
    }

    /**
     * Checks if the given type supports bitwise operations (Integer types excluding BigInteger).
     *
     * <pre>{@code
     * NumberUtils.isBitwiseType(Integer.class) = true
     * NumberUtils.isBitwiseType(BigInteger.class) = false
     * }</pre>
     *
     * @param type the class to check (maybe {@code null})
     * @return {@code true} if the type supports bitwise operations
     * @since 1.0.0
     */
    public static boolean isBitwiseType(Class<?> type) {
        return isIntegerType(type) && type != BigInteger.class;
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * SYSTEM IDENTIFICATION
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Checks if the string is a valid hexadecimal number.
     * <p>
     * Supports prefixes "0x", "0X", "#" and optional sign (+/-).
     * </p>
     *
     * <pre>{@code
     * NumberUtils.isHexadecimal("0x1A") = true
     * NumberUtils.isHexadecimal("#FF")  = true
     * }</pre>
     *
     * @param str the string to check (maybe {@code null})
     * @return {@code true} if the string is a valid hexadecimal number
     * @since 1.0.0
     */
    public static boolean isHexadecimal(String str) {
        if (str == null || str.isEmpty()) return false;
        int start = (str.startsWith(MINUS) || str.startsWith(PLUS)) ? 1 : 0;
        boolean hasPrefix = str.regionMatches(true, start, HEX_PREFIX_0X, 0, 2) || str.startsWith(HEX_PREFIX_HASH, start);
        if (!hasPrefix) return false;

        int dataStart = start + (str.startsWith(HEX_PREFIX_HASH, start) ? 1 : 2);
        if (dataStart == str.length()) return false;

        for (int i = dataStart; i < str.length(); i++) {
            char c = str.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) return false;
        }
        return true;
    }

    /**
     * Checks if the string is a valid octal number.
     * <p>
     * Must start with '0' and contain only digits 0-7.
     * </p>
     *
     * <pre>{@code
     * NumberUtils.isOctal("0123") = true
     * NumberUtils.isOctal("123")  = false
     * }</pre>
     *
     * @param str the string to check (maybe {@code null})
     * @return {@code true} if the string is a valid octal number
     * @since 1.0.0
     */
    public static boolean isOctal(String str) {
        if (str == null || str.isEmpty()) return false;
        int start = (str.startsWith(MINUS) || str.startsWith(PLUS)) ? 1 : 0;
        if (str.length() <= start + 1 || str.charAt(start) != '0' ||
                Character.toLowerCase(str.charAt(start + 1)) == 'x') return false;

        for (int i = start + 1; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c < '0' || c > '7') return false;
        }
        return true;
    }

    /**
     * Checks if the string is in scientific notation.
     *
     * <pre>{@code
     * NumberUtils.isScientificNotation("1.2e3") = true
     * }</pre>
     *
     * @param str the string to check (maybe {@code null})
     * @return {@code true} if the string is in scientific notation
     * @since 1.0.0
     */
    public static boolean isScientificNotation(String str) {
        if (str == null || str.isEmpty()) return false;
        int ePos = str.toLowerCase().indexOf(EXPONENT);
        return ePos > 0 && ePos < str.length() - 1 && isCreatable(str);
    }

    /**
     * Checks if the string can be parsed as a number.
     * <p>
     * This is a stricter check than {@link #isCreatable(String)}.
     * </p>
     *
     * <pre>{@code
     * NumberUtils.isParsable("123") = true
     * NumberUtils.isParsable("12.3") = true
     * }</pre>
     *
     * @param str the string to check (maybe {@code null})
     * @return {@code true} if the string is parsable
     * @since 1.0.0
     */
    public static boolean isParsable(String str) {
        if (str == null || str.isEmpty() || str.endsWith(".")) return false;
        int start = (str.startsWith(MINUS) || str.startsWith(PLUS)) ? 1 : 0;
        if (str.length() == start) return false;

        boolean hasDecPoint = false;
        for (int i = start; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == DECIMAL_POINT) {
                if (hasDecPoint) return false;
                hasDecPoint = true;
            } else if (!Character.isDigit(c)) return false;
        }
        return true;
    }

    /**
     * Checks if the string is a valid Java number literal.
     * <p>
     * Supports Hex, Octal, Scientific Notation, and Java type suffixes (L, F, D).
     * </p>
     *
     * <pre>{@code
     * NumberUtils.isCreatable("123L") = true
     * NumberUtils.isCreatable("0xAB") = true
     * }</pre>
     *
     * @param str the string to check (maybe {@code null})
     * @return {@code true} if the string is a valid number literal
     * @since 1.0.0
     */
    public static boolean isCreatable(String str) {
        if (str == null || str.isBlank()) return false;
        char[] chars = str.toCharArray();
        int sz = chars.length;
        boolean hasExp = false, hasDecPoint = false, foundDigit = false;
        int start = (chars[0] == '-' || chars[0] == '+') ? 1 : 0;

        if (sz > start + 1 && chars[start] == '0' && Character.toLowerCase(chars[start + 1]) == 'x') {
            for (int i = start + 2; i < sz; i++) {
                if (!((chars[i] >= '0' && chars[i] <= '9') || (chars[i] >= 'a' && chars[i] <= 'f') || (chars[i] >= 'A' && chars[i] <= 'F')))
                    return false;
                foundDigit = true;
            }
            return foundDigit;
        }

        for (int i = start; i < sz; i++) {
            if (Character.isDigit(chars[i])) foundDigit = true;
            else if (chars[i] == DECIMAL_POINT) {
                if (hasDecPoint || hasExp) return false;
                hasDecPoint = true;
            } else if (Character.toLowerCase(chars[i]) == EXPONENT) {
                if (hasExp || !foundDigit) return false;
                hasExp = true;
                if (i + 1 < sz && (chars[i + 1] == '-' || chars[i + 1] == '+')) i++;
            } else if (i == sz - 1 && isJavaSuffix(chars[i])) return foundDigit;
            else return false;
        }
        return foundDigit;
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * VALIDATION
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Checks if the number is zero.
     *
     * <pre>{@code
     * NumberUtils.isZero(0)            = true
     * NumberUtils.isZero(BigDecimal.ZERO) = true
     * }</pre>
     *
     * @param number the number to check (maybe {@code null})
     * @return {@code true} if the number is zero
     * @since 1.0.0
     */
    public static boolean isZero(Number number) {
        if (number == null) return false;
        if (number instanceof BigDecimal bd) return bd.compareTo(BigDecimal.ZERO) == 0;
        if (number instanceof BigInteger bi) return bi.equals(BigInteger.ZERO);
        return number.doubleValue() == 0.0;
    }

    /**
     * Checks if the number is positive (> 0).
     *
     * <pre>{@code
     * NumberUtils.isPositive(10) = true
     * NumberUtils.isPositive(-1) = false
     * }</pre>
     *
     * @param number the number to check (maybe {@code null})
     * @return {@code true} if the number is positive
     * @since 1.0.0
     */
    public static boolean isPositive(Number number) {
        if (number == null) return false;
        if (number instanceof BigDecimal bd) return bd.signum() > 0;
        if (number instanceof BigInteger bi) return bi.signum() > 0;
        return number.doubleValue() > 0.0;
    }

    /**
     * Checks if the number is negative (< 0).
     *
     * <pre>{@code
     * NumberUtils.isNegative(-1) = true
     * NumberUtils.isNegative(0)  = false
     * }</pre>
     *
     * @param number the number to check (maybe {@code null})
     * @return {@code true} if the number is negative
     * @since 1.0.0
     */
    public static boolean isNegative(Number number) {
        if (number == null) return false;
        if (number instanceof BigDecimal bd) return bd.signum() < 0;
        if (number instanceof BigInteger bi) return bi.signum() < 0;
        return number.doubleValue() < 0.0;
    }

    /**
     * Checks if the number is valid (not NaN and not Infinite).
     *
     * <pre>{@code
     * NumberUtils.isValidNumber(Double.NaN) = false
     * NumberUtils.isValidNumber(10.5)       = true
     * }</pre>
     *
     * @param number the number to check (maybe {@code null})
     * @return {@code true} if the number is valid
     * @since 1.0.0
     */
    public static boolean isValidNumber(Number number) {
        if (number instanceof Double d) return !d.isInfinite() && !d.isNaN();
        if (number instanceof Float f) return !f.isInfinite() && !f.isNaN();
        return number != null;
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * CONVERSION
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Helper for {@link #convertNumber(Number, Class)}.
     * Checks if a number fall within a specific integer range.
     */
    private static boolean isIntegerRange(Number number, long min, long max) {
        if (number instanceof BigInteger bi)
            return bi.compareTo(BigInteger.valueOf(min)) >= 0 && bi.compareTo(BigInteger.valueOf(max)) <= 0;
        if (number instanceof BigDecimal bd) return isIntegerRange(bd.toBigInteger(), min, max);
        if (number instanceof Double || number instanceof Float) {
            double d = number.doubleValue();
            if (d < (double) min || d > (double) max) return false;
        }
        long val = number.longValue();
        return val >= min && val <= max;
    }

    /**
     * Checks if the number falls within the range of a Long.
     *
     * <pre>{@code
     * NumberUtils.isLongRange(BigInteger.valueOf(Long.MAX_VALUE)) = true
     * }</pre>
     *
     * @param number the number to check (maybe {@code null})
     * @return {@code true} if the number fits in a Long
     * @since 1.0.0
     */
    public static boolean isLongRange(Number number) {
        if (number instanceof BigInteger bi)
            return bi.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) >= 0 && bi.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0;
        if (number instanceof Double || number instanceof Float || number instanceof BigDecimal) {
            double d = number.doubleValue();
            return !Double.isInfinite(d) && d >= (double) Long.MIN_VALUE && d <= (double) Long.MAX_VALUE;
        }
        return true;
    }

    /**
     * Converts a Number to a target class type.
     * <p>
     * Handles overflow checks for integer types.
     * </p>
     *
     * <pre>{@code
     * Integer i = NumberUtils.convertNumber(10L, Integer.class);
     * }</pre>
     *
     * @param <T>         the target type
     * @param number      the number to convert (maybe {@code null})
     * @param targetClass the target class (maybe {@code null})
     * @return the converted number, or {@code null} if input is null
     * @throws ArithmeticException if overflow occurs
     * @since 1.0.0
     */
    public static <T extends Number> T convertNumber(Number number, Class<T> targetClass) {
        if (number == null || targetClass == null) return null;
        if (targetClass.isInstance(number)) return targetClass.cast(number);

        if (targetClass == Byte.class || targetClass == byte.class) {
            if (!isIntegerRange(number, Byte.MIN_VALUE, Byte.MAX_VALUE)) raiseOverflow(number, targetClass);
            return targetClass.cast(number.byteValue());
        }
        if (targetClass == Short.class || targetClass == short.class) {
            if (!isIntegerRange(number, Short.MIN_VALUE, Short.MAX_VALUE)) raiseOverflow(number, targetClass);
            return targetClass.cast(number.shortValue());
        }
        if (targetClass == Integer.class || targetClass == int.class) {
            if (!isIntegerRange(number, Integer.MIN_VALUE, Integer.MAX_VALUE)) raiseOverflow(number, targetClass);
            return targetClass.cast(number.intValue());
        }
        if (targetClass == Long.class || targetClass == long.class) {
            if (!isLongRange(number)) raiseOverflow(number, targetClass);
            return targetClass.cast(number.longValue());
        }
        if (targetClass == BigDecimal.class) return targetClass.cast(new BigDecimal(number.toString()));
        if (targetClass == BigInteger.class)
            return targetClass.cast(number instanceof BigDecimal bd ? bd.toBigInteger() : BigInteger.valueOf(number.longValue()));

        // Float/Double fallback
        return targetClass.cast(targetClass == Float.class || targetClass == float.class ? number.floatValue() : number.doubleValue());
    }

    /**
     * Decodes a string into a number.
     * <p>
     * Supports Hex and Octal strings.
     * </p>
     *
     * <pre>{@code
     * Integer i = NumberUtils.decode("0x10", Integer.class); // 16
     * }</pre>
     *
     * @param <T>         the target type
     * @param text        the string to decode (maybe {@code null})
     * @param targetClass the target class (maybe {@code null})
     * @return the decoded number, or {@code null}
     * @since 1.0.0
     */
    public static <T extends Number> T decode(String text, Class<T> targetClass) {
        if (text == null || text.isBlank()) return null;
        String t = text.trim();
        if (isHexadecimal(t))
            return convertNumber(new BigInteger(t.replace(HEX_PREFIX_HASH, "").replace("0x", "").replace("0X", ""), 16), targetClass);
        if (isOctal(t)) return convertNumber(new BigInteger(t, 8), targetClass);
        return parse(t, targetClass);
    }

    /**
     * Parses a string into a number with strict or flexible mode.
     * <p>
     * Strategies:
     * </p>
     * <ul>
     * <li><b>Strict Mode (true):</b> Only allows plain numbers or scientific notation (e.g., "123", "-1.2e10").
     * Java suffixes (L, F, D) will throw an exception.</li>
     * <li><b>Flexible Mode (false):</b> Allows and strips Java type suffixes before parsing.</li>
     * </ul>
     *
     * <pre>{@code
     * NumberUtils.parse("123", Integer.class, true)  = 123
     * NumberUtils.parse("123L", Long.class, false)   = 123L
     * }</pre>
     *
     * @param <T>         the target type
     * @param text        the string to parse (maybe {@code null})
     * @param targetClass the target class (maybe {@code null})
     * @param strictMode  if {@code true}, disallows Java suffixes (L, F, D)
     * @return the parsed number, or {@code null}
     * @throws IllegalArgumentException if parsing fails or violates strict mode
     * @since 1.0.0
     */
    public static <T extends Number> T parse(String text, Class<T> targetClass, boolean strictMode) {
        if (text == null || text.isBlank() || targetClass == null) return null;

        String trimmed = text.trim();

        if (strictMode) {
            // Check for invalid characters in Strict Mode
            // (Only allow digits, dot, sign, and e/E)
            if (containsInvalidStrictChars(trimmed)) {
                throw new IllegalArgumentException("Strict parse failed: Suffixes are not allowed in [" + text + "]");
            }
        }

        // If not strictMode, strip suffixes
        String sanitized = strictMode ? trimmed : sanitizeJavaSuffix(trimmed);

        try {
            // BigDecimal handles both standard and scientific notation well
            BigDecimal bd = new BigDecimal(sanitized);
            return convertNumber(bd, targetClass);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Parse failed: [" + text + "] is not a valid number", e);
        }
    }

    /**
     * Parses a string into a number using flexible mode (allows suffixes).
     * <p>
     * This is a convenience method for {@link #parse(String, Class, boolean)} with {@code strictMode = false}.
     * </p>
     *
     * <pre>{@code
     * Long l = NumberUtils.parse("123L", Long.class);
     * }</pre>
     *
     * @param <T>         the target type
     * @param text        the string to parse (maybe {@code null})
     * @param targetClass the target class (maybe {@code null})
     * @return the parsed number, or {@code null}
     * @throws IllegalArgumentException if parsing fails
     * @see #parse(String, Class, boolean)
     * @since 1.0.0
     */
    public static <T extends Number> T parse(String text, Class<T> targetClass) {
        return parse(text, targetClass, false);
    }

    /**
     * Parses a string into a number, returning {@code defaultValue} if parsing fails.
     * <p>
     * Unlike {@link #parse(String, Class)}, this method never throws — invalid input
     * silently returns the provided default.
     * </p>
     *
     * <pre>{@code
     * NumberUtils.parseOrDefault("abc", Integer.class, 0)   // 0
     * NumberUtils.parseOrDefault("1.5", Double.class, 0.0)  // 1.5
     * }</pre>
     *
     * @param <T>          the target type
     * @param text         the string to parse (maybe {@code null})
     * @param targetClass  the target class (maybe {@code null})
     * @param defaultValue the value to return if parsing fails (maybe {@code null})
     * @return the parsed number, or {@code defaultValue}
     * @since 1.0.0
     */
    public static <T extends Number> T parseOrDefault(String text, Class<T> targetClass, T defaultValue) {
        try {
            return parse(text, targetClass);
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }

    /**
     * Helper to check for invalid characters in strict mode.
     * Allows digits, dot, sign, and scientific notation (e/E).
     */
    private static boolean containsInvalidStrictChars(String str) {
        // Hex is not considered strict decimal
        if (isHexadecimal(str)) return true;

        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            // Allow: digit, '.', 'e', 'E', '+', '-'
            if (!Character.isDigit(c) &&
                c != DECIMAL_POINT &&
                Character.toLowerCase(c) != EXPONENT &&
                c != '+' && c != '-') {
                return true;
            }
        }
        return false;
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * MATH HELPERS
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Compares two numbers.
     * <p>
     * Uses {@link BigDecimal} for comparison if types differ or are not Comparable.
     * </p>
     *
     * <pre>{@code
     * int result = NumberUtils.compare(10, 20); // -1
     * }</pre>
     *
     * @param n1 the first number (must not be {@code null})
     * @param n2 the second number (must not be {@code null})
     * @return -1, 0, or 1
     * @throws IllegalArgumentException if input is null
     * @since 1.0.0
     */
    @SuppressWarnings("all")
    public static int compare(Number n1, Number n2) {
        if (n1 == null || n2 == null) throw new IllegalArgumentException("Null input");
        if (n1.getClass() == n2.getClass() && n1 instanceof Comparable) return ((Comparable) n1).compareTo(n2);
        return toBigDecimal(n1).compareTo(toBigDecimal(n2));
    }

    /**
     * Returns the maximum value from an array of numbers.
     *
     * <pre>{@code
     * Integer max = NumberUtils.max(1, 5, 3); // 5
     * }</pre>
     *
     * @param <T>  the type of the numbers
     * @param vals the numbers to check (maybe {@code null})
     * @return the maximum value, or {@code null} if input is empty
     * @since 1.0.0
     */
    @SafeVarargs
    public static <T extends Number> T max(T... vals) {
        if (vals == null || vals.length == 0) return null;
        T m = vals[0];
        for (T v : vals) if (v != null && compare(v, m) > 0) m = v;
        return m;
    }

    /**
     * Returns the minimum value from an array of numbers.
     *
     * <pre>{@code
     * Integer min = NumberUtils.min(1, 5, 3); // 1
     * }</pre>
     *
     * @param <T>  the type of the numbersci
     * @param vals the numbers to check (maybe {@code null})
     * @return the minimum value, or {@code null} if input is empty
     * @since 1.0.0
     */
    @SafeVarargs
    public static <T extends Number> T min(T... vals) {
        if (vals == null || vals.length == 0) return null;
        T m = vals[0];
        for (T v : vals) if (v != null && compare(v, m) < 0) m = v;
        return m;
    }

    /**
     * Checks if the number is even.
     *
     * <pre>{@code
     * NumberUtils.isEven(2) = true
     * NumberUtils.isEven(3) = false
     * }</pre>
     *
     * @param n the number to check (maybe {@code null})
     * @return {@code true} if the number is even
     * @since 1.0.0
     */
    public static boolean isEven(Number n) {
        return n != null && (n instanceof BigInteger bi ? !bi.testBit(0) : n.longValue() % 2 == 0);
    }

    // --- Private Helpers ---

    /**
     * Helper to throw overflow exception.
     */
    private static void raiseOverflow(Number n, Class<?> t) {
        throw new ArithmeticException("Overflow: " + n + " for " + t.getSimpleName());
    }

    /**
     * Helper to check if a character is a valid Java numeric suffix.
     */
    private static boolean isJavaSuffix(char c) {
        for (char s : JAVA_NUMERIC_SUFFIXES) if (s == c) return true;
        return false;
    }

    /**
     * Helper to remove Java numeric suffix from a string.
     */
    private static String sanitizeJavaSuffix(String s) {
        if (s.isEmpty()) return s;
        char last = s.charAt(s.length() - 1);
        return (isJavaSuffix(last) && !isHexadecimal(s)) ? s.substring(0, s.length() - 1) : s;
    }

    /**
     * Helper to convert any Number to BigDecimal.
     */
    private static BigDecimal toBigDecimal(Number n) {
        if (n instanceof BigDecimal bd) return bd;
        if (n instanceof BigInteger bi) return new BigDecimal(bi);
        return new BigDecimal(n.toString());
    }
}
