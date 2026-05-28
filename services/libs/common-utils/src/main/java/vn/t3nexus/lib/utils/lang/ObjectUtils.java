/*
 * Created by Truong Ngo (2026).
 */
package vn.t3nexus.lib.utils.lang;

import java.lang.reflect.Array;
import java.util.*;
import java.util.function.Supplier;

/**
 * Operations on {@code Object}.
 * <p>
 * This class provides {@code null}-safe operations for checking emptiness,
 * handling default values, and comparing objects. It handles arrays correctly
 * where standard Java methods might fail or behave unexpectedly.
 * </p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 * <li>Null-safe defaults ({@code defaultIfNull}, {@code firstNonNull})</li>
 * <li>Emptiness checks for various types ({@code isEmpty}, {@code isNotEmpty})</li>
 * <li>Deep equality checks for arrays ({@code nullSafeEquals})</li>
 * <li>Identity and String representation utilities</li>
 * </ul>
 *
 * <p>This class is thread-safe and intended for static use.</p>
 *
 * @author Truong Ngo
 * @version 1.0.0
 * @since 1.0.0
 */
public final class ObjectUtils {

    /**
     * Prevents instantiation of this utility class.
     * @throws UnsupportedOperationException always
     */
    private ObjectUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * NULL SAFETY & DEFAULTS
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Returns a default value if the object passed is {@code null}.
     *
     * <pre>{@code
     * ObjectUtils.defaultIfNull(null, "default")  = "default"
     * ObjectUtils.defaultIfNull("value", "default") = "value"
     * }</pre>
     *
     * @param <T>          the type of the object
     * @param object       the object to test (maybe {@code null})
     * @param defaultValue the default value to return (maybe {@code null})
     * @return {@code object} if it is not {@code null}, {@code defaultValue} otherwise
     * @since 1.0.0
     */
    public static <T> T defaultIfNull(final T object, final T defaultValue) {
        return object != null ? object : defaultValue;
    }

    /**
     * Returns the first value if it is not {@code null}, otherwise returns the value
     * provided by the {@code supplier}.
     * <p>
     * This is useful for lazy evaluation of the default value (performance optimization).
     * </p>
     *
     * <pre>{@code
     * ObjectUtils.getIfNull(null, () -> "expensive") = "expensive"
     * }</pre>
     *
     * @param <T>             the type of the object
     * @param object          the object to test (maybe {@code null})
     * @param defaultSupplier the supplier for the default value (maybe {@code null})
     * @return {@code object} if not null, otherwise result of {@code defaultSupplier.get()}
     * @since 1.0.0
     */
    public static <T> T getIfNull(final T object, final Supplier<T> defaultSupplier) {
        return object != null ? object : (defaultSupplier == null ? null : defaultSupplier.get());
    }

    /**
     * Returns the first value that is not {@code null} in the array.
     *
     * <pre>{@code
     * ObjectUtils.firstNonNull(null, null, "A", "B") = "A"
     * }</pre>
     *
     * @param <T>    the type of the object
     * @param values the values to test (maybe {@code null} or empty)
     * @return the first non-null value, or {@code null} if there are no non-null values
     * @since 1.0.0
     */
    @SafeVarargs
    public static <T> T firstNonNull(final T... values) {
        if (values != null) {
            for (final T val : values) {
                if (val != null) {
                    return val;
                }
            }
        }
        return null;
    }

    /**
     * Checks if all values in the array are {@code null}.
     *
     * <pre>{@code
     * ObjectUtils.allNull(null, null) = true
     * ObjectUtils.allNull(null, "A")  = false
     * }</pre>
     *
     * @param values the values to test (maybe {@code null})
     * @return {@code true} if all values are {@code null} or the array is {@code null}
     * @since 1.0.0
     */
    public static boolean allNull(Object... values) {
        if (values == null) return true;
        for (Object val : values) {
            if (val != null) return false;
        }
        return true;
    }

    /**
     * Checks if any value in the array is {@code null}.
     *
     * <pre>{@code
     * ObjectUtils.anyNull("A", null) = true
     * ObjectUtils.anyNull("A", "B")  = false
     * }</pre>
     *
     * @param values the values to test (maybe {@code null})
     * @return {@code true} if any value is {@code null} or the array is {@code null}/empty
     * @since 1.0.0
     */
    public static boolean anyNull(Object... values) {
        if (values == null || values.length == 0) return true;
        for (Object val : values) {
            if (val == null) return true;
        }
        return false;
    }

    /**
     * Checks if all values in the array are NOT {@code null}.
     *
     * <pre>{@code
     * ObjectUtils.allNotNull("A", "B") = true
     * ObjectUtils.allNotNull("A", null) = false
     * }</pre>
     *
     * @param values the values to test (maybe {@code null})
     * @return {@code true} if all values are non-null
     * @since 1.0.0
     */
    public static boolean allNotNull(Object... values) {
        return !anyNull(values);
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * EMPTINESS CHECKS
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Determines whether the given object is empty.
     * <p>
     * This method supports the following types:
     * </p>
     * <ul>
     * <li>{@code Optional}: Considered empty if not present.</li>
     * <li>{@code Array}: Considered empty if length is 0.</li>
     * <li>{@code CharSequence}: Considered empty if length is 0.</li>
     * <li>{@code Collection}: Considered empty if it has no elements.</li>
     * <li>{@code Map}: Considered empty if it has no entries.</li>
     * </ul>
     *
     * <pre>{@code
     * ObjectUtils.isEmpty(null)       = true
     * ObjectUtils.isEmpty("")         = true
     * ObjectUtils.isEmpty(new int[]{}) = true
     * }</pre>
     *
     * @param obj the object to check (maybe {@code null})
     * @return {@code true} if the object is {@code null} or empty
     * @since 1.0.0
     */
    public static boolean isEmpty(Object obj) {
        if (obj == null) {
            return true;
        }
        if (obj instanceof Optional<?> optional) {
            return optional.isEmpty();
        }
        if (obj instanceof CharSequence charSequence) {
            return charSequence.isEmpty();
        }
        if (obj.getClass().isArray()) {
            return Array.getLength(obj) == 0;
        }
        if (obj instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        if (obj instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        return false;
    }

    /**
     * Checks if an Object is NOT empty and NOT null.
     *
     * <pre>{@code
     * ObjectUtils.isNotEmpty("A") = true
     * ObjectUtils.isNotEmpty("")  = false
     * }</pre>
     *
     * @param object the object to check (maybe {@code null})
     * @return {@code true} if the object is not null and not empty
     * @since 1.0.0
     */
    public static boolean isNotEmpty(final Object object) {
        return !isEmpty(object);
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * EQUALITY & ARRAYS
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Determines if the given objects are equal, returning {@code true} if
     * both are {@code null} or {@code false} if only one is {@code null}.
     * <p>
     * Crucially, this method compares the <b>content</b> of arrays (primitive and object arrays),
     * unlike {@link Objects#equals(Object, Object)} which compares array references.
     * </p>
     *
     * <pre>{@code
     * ObjectUtils.nullSafeEquals(new int[]{1}, new int[]{1}) = true
     * }</pre>
     *
     * @param o1 first object to compare (maybe {@code null})
     * @param o2 second object to compare (maybe {@code null})
     * @return {@code true} if the given objects are equal
     * @since 1.0.0
     */
    public static boolean nullSafeEquals(Object o1, Object o2) {
        if (o1 == o2) {
            return true;
        }
        if (o1 == null || o2 == null) {
            return false;
        }
        if (o1.equals(o2)) {
            return true;
        }
        if (o1.getClass().isArray() && o2.getClass().isArray()) {
            return arrayEquals(o1, o2);
        }
        return false;
    }

    /**
     * Helper to compare arrays content.
     * Delegates to {@link Arrays} standard methods.
     */
    private static boolean arrayEquals(Object o1, Object o2) {
        if (o1 instanceof Object[] && o2 instanceof Object[]) {
            return Arrays.deepEquals((Object[]) o1, (Object[]) o2);
        }
        if (o1 instanceof byte[] && o2 instanceof byte[]) {
            return Arrays.equals((byte[]) o1, (byte[]) o2);
        }
        if (o1 instanceof int[] && o2 instanceof int[]) {
            return Arrays.equals((int[]) o1, (int[]) o2);
        }
        if (o1 instanceof long[] && o2 instanceof long[]) {
            return Arrays.equals((long[]) o1, (long[]) o2);
        }
        if (o1 instanceof char[] && o2 instanceof char[]) {
            return Arrays.equals((char[]) o1, (char[]) o2);
        }
        if (o1 instanceof double[] && o2 instanceof double[]) {
            return Arrays.equals((double[]) o1, (double[]) o2);
        }
        if (o1 instanceof float[] && o2 instanceof float[]) {
            return Arrays.equals((float[]) o1, (float[]) o2);
        }
        if (o1 instanceof boolean[] && o2 instanceof boolean[]) {
            return Arrays.equals((boolean[]) o1, (boolean[]) o2);
        }
        if (o1 instanceof short[] && o2 instanceof short[]) {
            return Arrays.equals((short[]) o1, (short[]) o2);
        }
        return false;
    }

    /**
     * Compares two objects, handling {@code null} values safely.
     *
     * <pre>{@code
     * ObjectUtils.compare(null, "A", true)  = 1  (null is greater)
     * ObjectUtils.compare(null, "A", false) = -1 (null is smaller)
     * }</pre>
     *
     * @param <T>         the type of the objects
     * @param c1          the first object to compare (maybe {@code null})
     * @param c2          the second object to compare (maybe {@code null})
     * @param nullGreater if {@code true}, {@code null} is considered greater than non-null
     * @return a negative integer, zero, or a positive integer as {@code c1} is less than, equal to, or greater than {@code c2}
     * @since 1.0.0
     */
    public static <T extends Comparable<? super T>> int compare(T c1, T c2, boolean nullGreater) {
        if (c1 == null && c2 == null) return 0;
        if (c1 == c2) return 0;
        if (c1 == null) return nullGreater ? 1 : -1;
        if (c2 == null) return nullGreater ? -1 : 1;
        return c1.compareTo(c2);
    }

    /**
     * Compares two objects, handling {@code null} values safely (nulls are smaller).
     *
     * <pre>{@code
     * ObjectUtils.compare("A", "B") = -1
     * }</pre>
     *
     * @param <T> the type of the objects
     * @param c1  the first object to compare (maybe {@code null})
     * @param c2  the second object to compare (maybe {@code null})
     * @return a negative integer, zero, or a positive integer
     * @see #compare(Comparable, Comparable, boolean)
     * @since 1.0.0
     */
    public static <T extends Comparable<? super T>> int compare(T c1, T c2) {
        return compare(c1, c2, false);
    }

    /**
     * Null-safe check if an object is an array.
     *
     * <pre>{@code
     * ObjectUtils.isArray(new int[]{}) = true
     * ObjectUtils.isArray("string")    = false
     * }</pre>
     *
     * @param object the object to check (maybe {@code null})
     * @return {@code true} if the object is an array
     * @since 1.0.0
     */
    public static boolean isArray(Object object) {
        return object != null && object.getClass().isArray();
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * IDENTITY & DEBUGGING
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Returns the hex string form of an object's identity hash code.
     * <p>
     * This method mimics the default {@code toString()} behavior of the {@code Object} class.
     * It is useful for debugging to verify if two variables refer to the exact same object in memory.
     * </p>
     *
     * <pre>{@code
     * String id = ObjectUtils.identityToString(myObj); // "MyClass@1a2b3c"
     * }</pre>
     *
     * @param obj the object (maybe {@code null})
     * @return the identity string, or {@code null} if the object is null
     * @since 1.0.0
     */
    public static String identityToString(Object obj) {
        if (obj == null) {
            return null;
        }
        return obj.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(obj));
    }

    /**
     * Returns a String representation of the specified object.
     * <p>
     * If the object is an array, it returns a string representation of the array contents.
     * Otherwise, it returns {@code obj.toString()}.
     * </p>
     *
     * <pre>{@code
     * ObjectUtils.nullSafeToString(new int[]{1, 2}) = "[1, 2]"
     * ObjectUtils.nullSafeToString(null)            = "null"
     * }</pre>
     *
     * @param obj the object to convert to a String (maybe {@code null})
     * @return the String representation (never {@code null})
     * @since 1.0.0
     */
    public static String nullSafeToString(Object obj) {
        if (obj == null) {
            return "null";
        }
        if (obj instanceof String string) {
            return string;
        }
        if (obj instanceof Object[] objs) {
            return Arrays.deepToString(objs);
        }
        if (obj instanceof byte[] bytes) {
            return Arrays.toString(bytes);
        }
        if (obj instanceof int[] integers) {
            return Arrays.toString(integers);
        }
        if (obj instanceof long[] longs) {
            return Arrays.toString(longs);
        }
        if (obj instanceof char[] chars) {
            return Arrays.toString(chars);
        }
        if (obj instanceof double[] doubles) {
            return Arrays.toString(doubles);
        }
        if (obj instanceof float[] floats) {
            return Arrays.toString(floats);
        }
        if (obj instanceof boolean[] booleans) {
            return Arrays.toString(booleans);
        }
        if (obj instanceof short[] shorts) {
            return Arrays.toString(shorts);
        }
        return obj.toString();
    }
}
