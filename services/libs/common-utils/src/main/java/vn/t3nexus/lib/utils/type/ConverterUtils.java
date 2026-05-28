/*
 * Created by Truong Ngo (2026).
 */
package vn.t3nexus.lib.utils.type;

import vn.t3nexus.lib.utils.lang.DateUtils;
import vn.t3nexus.lib.utils.lang.NumberUtils;
import vn.t3nexus.lib.utils.reflect.ClassUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Utility for type conversion.
 * <p>
 * This class acts as a central coordinator for converting between different data types.
 * It leverages {@link NumberUtils} and {@link DateUtils} for specialized conversions
 * and provides a registry for custom converters.
 * </p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 * <li><b>Number Conversion:</b> String to Number, Number to Number (with overflow checks).</li>
 * <li><b>Date Conversion:</b> String to Date/Time, Timestamp to Date/Time.</li>
 * <li><b>Logic Conversion:</b> String to Boolean (supports "true", "1", "y", "on").</li>
 * <li><b>Extensibility:</b> Register custom converters via {@link #register(Class, Class, Function)}.</li>
 * </ul>
 *
 * <p><b>Memory safety:</b> The registry uses a two-level
 * {@code WeakHashMap<source Class, ConcurrentHashMap<target Class, converter>>}.
 * When a classloader is unloaded, its {@code Class} objects become weakly reachable and
 * the corresponding registry entries are automatically removed — preventing classloader leaks
 * in OSGi, hot-reload, and multi-classloader environments.</p>
 *
 * <p>This class is thread-safe and intended for static use.</p>
 *
 * @author Truong Ngo
 * @version 1.0.0
 * @since 1.0.0
 */
public class ConverterUtils {

    /**
     * Two-level map: WeakHashMap<source Class> → ConcurrentHashMap<target Class> → converter.
     * <br>
     * WeakHashMap outer: source Class is the weak key — when its classloader is unloaded the
     *   entry (and the inner map) is eligible for GC, preventing classloader leaks.
     * ConcurrentHashMap inner: target Class lookup is lock-free after the outer get.
     * <br>
     * Mutations on the outer map (computeIfAbsent in register/cache) must synchronize on the
     * map itself — the standard contract for Collections.synchronizedMap.
     */
    private static final Map<Class<?>, ConcurrentHashMap<Class<?>, Function<Object, Object>>> REGISTRY =
            Collections.synchronizedMap(new WeakHashMap<>(32));

    private static final Map<Class<?>, ConcurrentHashMap<Class<?>, Function<Object, Object>>> COMPATIBLE_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>(16));

    static {
        registerDefaultConverters();
    }

    /**
     * Prevents instantiation of this utility class.
     * @throws UnsupportedOperationException always
     */
    private ConverterUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * INITIALIZATION
     * -----------------------------------------------------------------------------------------------------------------
     */

    private static void registerDefaultConverters() {
        // --- 1. String -> Number ---
        register(String.class, Byte.class,       s -> NumberUtils.parse(s, Byte.class));
        register(String.class, Short.class,      s -> NumberUtils.parse(s, Short.class));
        register(String.class, Integer.class,    s -> NumberUtils.parse(s, Integer.class));
        register(String.class, Long.class,       s -> NumberUtils.parse(s, Long.class));
        register(String.class, Float.class,      s -> NumberUtils.parse(s, Float.class));
        register(String.class, Double.class,     s -> NumberUtils.parse(s, Double.class));
        register(String.class, BigDecimal.class, s -> NumberUtils.parse(s, BigDecimal.class));
        register(String.class, BigInteger.class, s -> NumberUtils.parse(s, BigInteger.class));

        // --- 2. Number -> Number (overflow-checked via NumberUtils.convertNumber) ---
        register(Number.class, Byte.class,       n -> NumberUtils.convertNumber(n, Byte.class));
        register(Number.class, Short.class,      n -> NumberUtils.convertNumber(n, Short.class));
        register(Number.class, Integer.class,    n -> NumberUtils.convertNumber(n, Integer.class));
        register(Number.class, Long.class,       n -> NumberUtils.convertNumber(n, Long.class));
        register(Number.class, Float.class,      n -> NumberUtils.convertNumber(n, Float.class));
        register(Number.class, Double.class,     n -> NumberUtils.convertNumber(n, Double.class));
        register(Number.class, BigDecimal.class, n -> NumberUtils.convertNumber(n, BigDecimal.class));
        register(Number.class, BigInteger.class, n -> NumberUtils.convertNumber(n, BigInteger.class));

        // --- 3. String -> Date (heuristic parsing via DateUtils) ---
        register(String.class, java.time.LocalDate.class,     DateUtils::parseLocalDate);
        register(String.class, java.time.LocalDateTime.class, DateUtils::parseLocalDateTime);
        register(String.class, Date.class,          s -> DateUtils.parseDate(s, java.time.ZoneId.systemDefault()));

        // --- 4. Long (epoch millis) -> Date ---
        register(Long.class, Date.class,             l -> DateUtils.parseAsMilliWithSystemZone(l, Date.class));
        register(Long.class, java.time.LocalDateTime.class,    l -> DateUtils.parseAsMilliWithSystemZone(l, java.time.LocalDateTime.class));
        register(Long.class, Instant.class,          Instant::ofEpochMilli);

        // --- 5. String -> Character ---
        register(String.class, Character.class, s -> {
            if (s.length() != 1) throw new IllegalArgumentException("Cannot convert \"" + s + "\" to Character: length must be 1");
            return s.charAt(0);
        });

        // --- 6. String -> Boolean ---
        register(String.class, Boolean.class, s -> {
            String val = s.trim().toLowerCase();
            return "true".equals(val) || "1".equals(val) || "y".equals(val) || "on".equals(val);
        });
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * CONVERSION LOGIC
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Converts the source object to the target type.
     * <p>
     * Resolution order:
     * <ol>
     * <li>Direct cast if source is already an instance of targetType.</li>
     * <li>Exact match in the converter registry.</li>
     * <li>Hierarchy-based match (cached after first lookup).</li>
     * <li>Enum conversion from String.</li>
     * <li>Fallback to {@code toString()} if targetType is {@code String}.</li>
     * </ol>
     * </p>
     *
     * <pre>{@code
     * Integer i = ConverterUtils.convert("123", Integer.class); // 123
     * Boolean b = ConverterUtils.convert("on", Boolean.class);  // true
     * }</pre>
     *
     * @param <T>        the target type
     * @param source     the source object to convert (maybe {@code null})
     * @param targetType the target class type (must not be {@code null})
     * @return the converted object, or {@code null} if source is null
     * @throws IllegalArgumentException if no suitable converter is found
     * @since 1.0.0
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static <T> T convert(Object source, Class<T> targetType) {
        if (source == null) return null;
        if (targetType.isInstance(source)) return targetType.cast(source);

        Class<?> sType = ClassUtils.resolvePrimitiveIfNecessary(source.getClass());
        Class<?> tType = ClassUtils.resolvePrimitiveIfNecessary(targetType);

        // 1. Exact match
        Function<Object, Object> converter = lookupExact(sType, tType);

        // 2. Hierarchy match (result cached for subsequent calls)
        if (converter == null) {
            converter = findCompatibleConverter(sType, tType);
        }

        if (converter != null) return (T) converter.apply(source);

        // 3. Enum from String
        if (tType.isEnum() && source instanceof String s) {
            return (T) Enum.valueOf((Class<Enum>) tType, s);
        }

        // 4. Any -> String fallback
        if (tType == String.class) return (T) source.toString();

        throw new IllegalArgumentException("No converter registered for " + sType.getName() + " -> " + tType.getName());
    }

    /**
     * Registers a custom converter.
     * <p>
     * Registering a new converter clears the compatible cache to avoid stale hierarchy matches.
     * </p>
     *
     * <pre>{@code
     * ConverterUtils.register(String.class, MyType.class, s -> new MyType(s));
     * }</pre>
     *
     * @param <S>    the source type
     * @param <T>    the target type
     * @param source the source class (must not be {@code null})
     * @param target the target class (must not be {@code null})
     * @param func   the conversion function (must not be {@code null})
     * @since 1.0.0
     */
    @SuppressWarnings("unchecked")
    public static <S, T> void register(Class<S> source, Class<T> target, Function<S, T> func) {
        synchronized (REGISTRY) {
            REGISTRY.computeIfAbsent(source, k -> new ConcurrentHashMap<>())
                    .put(target, (Function<Object, Object>) func);
        }
        COMPATIBLE_CACHE.clear();
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * INTERNAL HELPERS
     * -----------------------------------------------------------------------------------------------------------------
     */

    private static Function<Object, Object> lookupExact(Class<?> sType, Class<?> tType) {
        ConcurrentHashMap<Class<?>, Function<Object, Object>> targets = REGISTRY.get(sType);
        return targets != null ? targets.get(tType) : null;
    }

    private static Function<Object, Object> findCompatibleConverter(Class<?> sType, Class<?> tType) {
        // 1. Check compatible cache first
        ConcurrentHashMap<Class<?>, Function<Object, Object>> cached = COMPATIBLE_CACHE.get(sType);
        if (cached != null) {
            Function<Object, Object> func = cached.get(tType);
            if (func != null) return func;
        }

        // 2. Scan registry under lock — avoid Class.forName, direct isAssignableFrom
        Function<Object, Object> found = null;
        synchronized (REGISTRY) {
            outer:
            for (Map.Entry<Class<?>, ConcurrentHashMap<Class<?>, Function<Object, Object>>> outer : REGISTRY.entrySet()) {
                if (!outer.getKey().isAssignableFrom(sType)) continue;
                for (Map.Entry<Class<?>, Function<Object, Object>> inner : outer.getValue().entrySet()) {
                    if (tType.isAssignableFrom(inner.getKey())) {
                        found = inner.getValue();
                        break outer;
                    }
                }
            }
        }

        // 3. Cache result outside registry lock to avoid nested locking
        if (found != null) {
            synchronized (COMPATIBLE_CACHE) {
                COMPATIBLE_CACHE.computeIfAbsent(sType, k -> new ConcurrentHashMap<>()).put(tType, found);
            }
        }
        return found;
    }
}
