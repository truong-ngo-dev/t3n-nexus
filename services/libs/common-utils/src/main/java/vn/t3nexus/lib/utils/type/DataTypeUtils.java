/*
 * Created by Truong Ngo (2026).
 */
package vn.t3nexus.lib.utils.type;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.*;
import java.time.temporal.Temporal;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Utility for classifying and inspecting Java data types.
 * <p>
 * This utility provides high-performance O(1) checks for standard types using internal caches,
 * while also supporting comprehensive hierarchy-based checks (Spring-style).
 * </p>
 *
 * <p><b>Key Concepts:</b></p>
 * <ul>
 * <li><b>Standard Types:</b> Leaf-node types like primitives, wrappers, String, and common Dates.</li>
 * <li><b>Comprehensive Types:</b> Includes standard types plus their subclasses (e.g., AtomicInteger as Number).</li>
 * </ul>
 *
 * <p>This class is thread-safe and intended for static use.</p>
 *
 * @author Truong Ngo
 * @version 1.0.0
 * @since 1.0.0
 */
public class DataTypeUtils {

    /**
     * Prevents instantiation of this utility class.
     * @throws UnsupportedOperationException always
     */
    private DataTypeUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * CACHE & CONSTANTS (O(1) Lookup)
     * -----------------------------------------------------------------------------------------------------------------
     */

    // --- CACHE GROUPS ---
    private static final Set<Class<?>> PRIMITIVE_NUMBERS;
    private static final Set<Class<?>> WRAPPER_NUMBERS;
    private static final Set<Class<?>> STANDARD_NUMBERS;

    private static final Set<Class<?>> MODERN_DATE_TYPES; // Immutable (Java 8+)
    private static final Set<Class<?>> LEGACY_DATE_TYPES; // Mutable (Old Java)
    private static final Set<Class<?>> STANDARD_DATES;

    private static final Set<Class<?>> SPECIAL_VALUE_TYPES;

    /*
     * Initializes the static cache/mapping.
     * Loads configuration from standard types safely.
     */
    static {
        // 1. Primitive Numbers
        PRIMITIVE_NUMBERS = Set.of(byte.class, short.class, int.class, long.class, float.class, double.class);

        // 2. Wrapper Numbers
        WRAPPER_NUMBERS = Set.of(
                Byte.class, Short.class, Integer.class, Long.class, Float.class, Double.class
        );

        Set<Class<?>> sn = new HashSet<>(32);
        sn.addAll(PRIMITIVE_NUMBERS);
        sn.addAll(WRAPPER_NUMBERS);
        sn.add(BigInteger.class);
        sn.add(BigDecimal.class);
        STANDARD_NUMBERS = Collections.unmodifiableSet(sn);

        MODERN_DATE_TYPES = Set.of(
                LocalDate.class, LocalDateTime.class, LocalTime.class,
                ZonedDateTime.class, OffsetDateTime.class, OffsetTime.class,
                Instant.class, Duration.class, Period.class
        );

        // Legacy Group: Mutable, high risk of side effects
        LEGACY_DATE_TYPES = Set.of(
                Date.class, Calendar.class,
                java.sql.Date.class, java.sql.Timestamp.class, java.sql.Time.class
        );

        Set<Class<?>> all = new HashSet<>();
        all.addAll(MODERN_DATE_TYPES);
        all.addAll(LEGACY_DATE_TYPES);
        STANDARD_DATES = Collections.unmodifiableSet(all);

        // Final Classes Group (Use Set.contains for O(1) speed)
        SPECIAL_VALUE_TYPES = Set.of(UUID.class, URL.class, URI.class, Locale.class, Pattern.class, Class.class, Currency.class, Charset.class, java.io.File.class);
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * NUMBER
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Checks if the given type is a primitive number (byte, short, int, long, float, double).
     * <p>
     * This check is O(1). Does not include {@code boolean} or {@code char}.
     * </p>
     *
     * <pre>{@code
     * DataTypeUtils.isPrimitiveNumber(int.class)     = true
     * DataTypeUtils.isPrimitiveNumber(boolean.class) = false
     * DataTypeUtils.isPrimitiveNumber(Integer.class) = false
     * }</pre>
     *
     * @param type the class to check (maybe {@code null})
     * @return {@code true} if the type is a numeric primitive
     * @since 1.0.0
     */
    public static boolean isPrimitiveNumber(Class<?> type) {
        return type != null && PRIMITIVE_NUMBERS.contains(type);
    }

    /**
     * Checks if the given type is a numeric wrapper (Byte, Short, Integer, Long, Float, Double).
     * <p>
     * This check is O(1). Does not include {@code Boolean}, {@code Character}, or {@code Void}.
     * </p>
     *
     * <pre>{@code
     * DataTypeUtils.isWrapperNumber(Integer.class) = true
     * DataTypeUtils.isWrapperNumber(Boolean.class) = false
     * DataTypeUtils.isWrapperNumber(int.class)     = false
     * }</pre>
     *
     * @param type the class to check (maybe {@code null})
     * @return {@code true} if the type is a numeric wrapper
     * @since 1.0.0
     */
    public static boolean isWrapperNumber(Class<?> type) {
        return type != null && WRAPPER_NUMBERS.contains(type);
    }

    /**
     * Checks if the given type is a big number (BigDecimal or BigInteger).
     *
     * <pre>{@code
     * DataTypeUtils.isBigNumber(BigDecimal.class) = true
     * DataTypeUtils.isBigNumber(BigInteger.class) = true
     * DataTypeUtils.isBigNumber(Double.class)     = false
     * }</pre>
     *
     * @param type the class to check (maybe {@code null})
     * @return {@code true} if the type is BigDecimal or BigInteger
     * @since 1.0.0
     */
    public static boolean isBigNumber(Class<?> type) {
        return type == BigDecimal.class || type == BigInteger.class;
    }

    /**
     * Checks if the given type is a standard number (Primitive, Wrapper, BigInteger, BigDecimal).
     * <p>
     * This check is O(1) and does not include custom Number subclasses.
     * </p>
     *
     * <pre>{@code
     * DataTypeUtils.isStandardNumber(Integer.class)    = true
     * DataTypeUtils.isStandardNumber(AtomicInteger.class) = false
     * }</pre>
     *
     * @param type the class to check (maybe {@code null})
     * @return {@code true} if the type is a standard number
     * @since 1.0.0
     */
    public static boolean isStandardNumber(Class<?> type) {
        return type != null && STANDARD_NUMBERS.contains(type);
    }

    /**
     * Checks if the given type is any kind of Number.
     * <p>
     * This includes standard numbers and any class extending {@link Number}.
     * </p>
     *
     * <pre>{@code
     * DataTypeUtils.isNumber(AtomicInteger.class) = true
     * }</pre>
     *
     * @param type the class to check (maybe {@code null})
     * @return {@code true} if the type is a number
     * @since 1.0.0
     */
    public static boolean isNumber(Class<?> type) {
        if (type == null) return false;
        return isStandardNumber(type) || Number.class.isAssignableFrom(type);
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * DATE
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Checks if the given type is a standard date type (Java 8+ or Legacy).
     * <p>
     * This check is O(1).
     * </p>
     *
     * <pre>{@code
     * DataTypeUtils.isStandardDate(LocalDate.class) = true
     * DataTypeUtils.isStandardDate(java.util.Date.class) = true
     * }</pre>
     *
     * @param type the class to check (maybe {@code null})
     * @return {@code true} if the type is a standard date
     * @since 1.0.0
     */
    public static boolean isStandardDate(Class<?> type) {
        return type != null && STANDARD_DATES.contains(type);
    }

    /**
     * Checks if the given type is a modern Java 8+ date type (Immutable).
     *
     * <pre>{@code
     * DataTypeUtils.isModernDate(LocalDate.class) = true
     * }</pre>
     *
     * @param type the class to check (maybe {@code null})
     * @return {@code true} if the type is a modern date
     * @since 1.0.0
     */
    public static boolean isModernDate(Class<?> type) {
        return type != null && MODERN_DATE_TYPES.contains(type);
    }

    /**
     * Checks if the given type is a legacy date type (Mutable).
     *
     * <pre>{@code
     * DataTypeUtils.isLegacyDate(java.util.Date.class) = true
     * }</pre>
     *
     * @param type the class to check (maybe {@code null})
     * @return {@code true} if the type is a legacy date
     * @since 1.0.0
     */
    public static boolean isLegacyDate(Class<?> type) {
        return type != null && LEGACY_DATE_TYPES.contains(type);
    }

    /**
     * Checks if the given type is any kind of Date or Temporal.
     *
     * <pre>{@code
     * DataTypeUtils.isDate(LocalDate.class) = true
     * DataTypeUtils.isDate(MyCustomDate.class) = true (if extends Date/Temporal)
     * }</pre>
     *
     * @param type the class to check (maybe {@code null})
     * @return {@code true} if the type is a date
     * @since 1.0.0
     */
    public static boolean isDate(Class<?> type) {
        if (type == null) return false;
        return STANDARD_DATES.contains(type) ||
               Temporal.class.isAssignableFrom(type) ||
               Date.class.isAssignableFrom(type);
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * CONTAINER
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Checks if the given type is a container (Array, Iterable, or Map).
     *
     * <pre>{@code
     * DataTypeUtils.isContainer(List.class) = true
     * DataTypeUtils.isContainer(String[].class) = true
     * }</pre>
     *
     * @param type the class to check (maybe {@code null})
     * @return {@code true} if the type is a container
     * @since 1.0.0
     */
    public static boolean isContainer(Class<?> type) {
        if (type == null) return false;
        return type.isArray() ||
               Iterable.class.isAssignableFrom(type) ||
               Map.class.isAssignableFrom(type);
    }

    /**
     * Checks if the given type is an array.
     *
     * <pre>{@code
     * DataTypeUtils.isArray(int[].class) = true
     * }</pre>
     *
     * @param type the class to check (maybe {@code null})
     * @return {@code true} if the type is an array
     * @since 1.0.0
     */
    public static boolean isArray(Class<?> type) {
        return type != null && type.isArray();
    }

    /**
     * Checks if the given type is a Collection.
     *
     * <pre>{@code
     * DataTypeUtils.isCollection(List.class) = true
     * }</pre>
     *
     * @param type the class to check (maybe {@code null})
     * @return {@code true} if the type is a collection
     * @since 1.0.0
     */
    public static boolean isCollection(Class<?> type) {
        return type != null && Collection.class.isAssignableFrom(type);
    }

    /**
     * Checks if the given type is a Map.
     *
     * <pre>{@code
     * DataTypeUtils.isMap(HashMap.class) = true
     * }</pre>
     *
     * @param type the class to check (maybe {@code null})
     * @return {@code true} if the type is a map
     * @since 1.0.0
     */
    public static boolean isMap(Class<?> type) {
        return type != null && Map.class.isAssignableFrom(type);
    }

    /**
     * Checks if the given type is Iterable.
     *
     * <pre>{@code
     * DataTypeUtils.isIterable(Set.class) = true
     * }</pre>
     *
     * @param type the class to check (maybe {@code null})
     * @return {@code true} if the type is iterable
     * @since 1.0.0
     */
    public static boolean isIterable(Class<?> type) {
        return type != null && Iterable.class.isAssignableFrom(type);
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * OTHER
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Checks if the given type is an Enum.
     *
     * <pre>{@code
     * DataTypeUtils.isEnumType(MyEnum.class) = true
     * }</pre>
     *
     * @param type the class to check (maybe {@code null})
     * @return {@code true} if the type is an enum
     * @since 1.0.0
     */
    public static boolean isEnumType(Class<?> type) {
        if (type == null) return false;
        return type.isEnum() || (type.getSuperclass() != null && type.getSuperclass().isEnum());
    }

    /**
     * Checks if the given type is a Boolean (primitive or wrapper).
     *
     * <pre>{@code
     * DataTypeUtils.isBoolean(boolean.class) = true
     * }</pre>
     *
     * @param type the class to check (maybe {@code null})
     * @return {@code true} if the type is a boolean
     * @since 1.0.0
     */
    public static boolean isBoolean(Class<?> type) {
        return type == boolean.class || type == Boolean.class;
    }

    /**
     * Checks if the given type is a logic type (Boolean or AtomicBoolean).
     *
     * <pre>{@code
     * DataTypeUtils.isLogicType(boolean.class)      = true
     * DataTypeUtils.isLogicType(AtomicBoolean.class) = true
     * }</pre>
     *
     * @param type the class to check (maybe {@code null})
     * @return {@code true} if the type is a logic type
     * @since 1.0.0
     */
    public static boolean isLogicType(Class<?> type) {
        return isBoolean(type) || type == AtomicBoolean.class;
    }

    /**
     * Checks if the given type is a Character (primitive or wrapper).
     *
     * <pre>{@code
     * DataTypeUtils.isCharacter(char.class) = true
     * }</pre>
     *
     * @param type the class to check (maybe {@code null})
     * @return {@code true} if the type is a character
     * @since 1.0.0
     */
    public static boolean isCharacter(Class<?> type) {
        return type == char.class || type == Character.class;
    }

    /**
     * Checks if the given type is a String.
     *
     * <pre>{@code
     * DataTypeUtils.isString(String.class) = true
     * }</pre>
     *
     * @param type the class to check (maybe {@code null})
     * @return {@code true} if the type is a string
     * @since 1.0.0
     */
    public static boolean isString(Class<?> type) {
        return type == String.class;
    }

    /**
     * Checks if the given type is a CharSequence.
     *
     * <pre>{@code
     * DataTypeUtils.isCharSequence(StringBuilder.class) = true
     * }</pre>
     *
     * @param type the class to check (maybe {@code null})
     * @return {@code true} if the type is a char sequence
     * @since 1.0.0
     */
    public static boolean isCharSequence(Class<?> type) {
        return type != null && CharSequence.class.isAssignableFrom(type);
    }

    /**
     * Checks if the given type is void.
     *
     * <pre>{@code
     * DataTypeUtils.isVoid(void.class) = true
     * }</pre>
     *
     * @param type the class to check (maybe {@code null})
     * @return {@code true} if the type is void
     * @since 1.0.0
     */
    public static boolean isVoid(Class<?> type) {
        return type == void.class || type == Void.class;
    }

    /**
     * Checks if the given type is a special value type (e.g., UUID, URL, Path).
     *
     * <pre>{@code
     * DataTypeUtils.isSpecialValueType(UUID.class) = true
     * }</pre>
     *
     * @param type the class to check (maybe {@code null})
     * @return {@code true} if the type is a special value type
     * @since 1.0.0
     */
    public static boolean isSpecialValueType(Class<?> type) {
        if (type == null) return false;
        if (SPECIAL_VALUE_TYPES.contains(type)) return true;
        return ZoneId.class.isAssignableFrom(type) ||
               TimeZone.class.isAssignableFrom(type) ||
               java.nio.file.Path.class.isAssignableFrom(type) ||
               java.net.InetAddress.class.isAssignableFrom(type);
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * SUMMARY METHODS
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Checks if the type is a common data type found in POJOs (Fast-track).
     * <p>
     * Uses 100% O(1) Cache for maximum performance. Suitable for metadata scanning loops
     * or shallow deep cloning.
     * </p>
     *
     * <pre>{@code
     * DataTypeUtils.isCommonType(String.class) = true
     * DataTypeUtils.isCommonType(Integer.class) = true
     * }</pre>
     *
     * @param type the class to check (maybe {@code null})
     * @return {@code true} if it belongs to String, Standard Number, Standard Date, Boolean, or Enum groups
     * @since 1.0.0
     */
    public static boolean isCommonType(Class<?> type) {
        return isString(type) ||
               isCharacter(type) ||
               isStandardNumber(type) ||
               isStandardDate(type) ||
               isBoolean(type) ||
               isEnumType(type);
    }

    /**
     * Checks if the type is considered a "simple value type" (Comprehensive).
     * <p>
     * Includes the {@link #isCommonType(Class)} group and special JDK types (Spring-style)
     * such as UUID, File, InetAddress, Path...
     * </p>
     *
     * <pre>{@code
     * DataTypeUtils.isSimpleValueType(UUID.class) = true
     * }</pre>
     *
     * @param type the class to check (maybe {@code null})
     * @return {@code true} if it is considered a simple data unit
     * @since 1.0.0
     */
    public static boolean isSimpleValueType(Class<?> type) {
        if (type == null || isVoid(type)) {
            return false;
        }
        // Prioritize checking the common group first due to high occurrence rate (Short-circuiting)
        if (isCommonType(type)) {
            return true;
        }
        // Finally check special types or use hierarchy check
        return isSpecialValueType(type) || isNumber(type) || isDate(type) || isCharSequence(type) || isLogicType(type);
    }
}
