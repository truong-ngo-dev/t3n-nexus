/*
 * Created by Truong Ngo (2026).
 */
package vn.t3nexus.lib.utils.lang;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Assertion utility class that assists in validating arguments.
 * <p>
 * This class is useful for identifying programmer errors early at runtime.
 * It provides methods to check for nulls, empty collections, boolean expressions, and text.
 * </p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 * <li>Boolean assertions ({@code isTrue})</li>
 * <li>Null checks ({@code notNull})</li>
 * <li>Text validation ({@code hasText})</li>
 * <li>Collection/Map emptiness checks ({@code notEmpty})</li>
 * </ul>
 *
 * <p>This class is thread-safe and intended for static use.</p>
 *
 * @author Truong Ngo
 * @version 1.0.0
 * @since 1.0.0
 */
public final class Assert {

    /**
     * Prevents instantiation of this utility class.
     * @throws UnsupportedOperationException always
     */
    private Assert() {
        throw new UnsupportedOperationException("Utility class");
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * BOOLEAN CHECKS
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Asserts that a boolean expression is {@code true}.
     *
     * <pre>{@code
     * Assert.isTrue(i > 0, "The value must be greater than zero");
     * }</pre>
     *
     * @param expression the boolean expression to check
     * @param message    the exception message to use if the assertion fails (must not be {@code null})
     * @throws IllegalArgumentException if the expression is {@code false}
     * @since 1.0.0
     */
    public static void isTrue(boolean expression, String message) {
        if (!expression) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Asserts that a boolean expression is {@code true}, executing a custom action if false.
     * <p>
     * This method allows for custom error handling logic, such as logging or throwing a specific exception,
     * using a context object provided by a supplier.
     * </p>
     *
     * <pre>{@code
     * Assert.isTrue(valid, "Invalid state", () -> myContext, (ctx, msg) -> {
     *     log.error("Error in context {}: {}", ctx, msg);
     *     throw new MyCustomException(msg);
     * });
     * }</pre>
     *
     * @param <T>             the type of the context object
     * @param expression      the boolean expression to check
     * @param message         the message to pass to the action (must not be {@code null})
     * @param contextSupplier a supplier for the context object (maybe {@code null})
     * @param actionIfFalse   the action to execute if the expression is {@code false} (must not be {@code null})
     * @throws NullPointerException if {@code actionIfFalse} is {@code null}
     * @since 1.0.0
     */
    public static <T> void isTrue(boolean expression, String message, Supplier<T> contextSupplier, BiConsumer<T, String> actionIfFalse) {
        Objects.requireNonNull(actionIfFalse, "actionIfFalse must not be null");
        if (!expression) {
            T context = Optional.ofNullable(contextSupplier).map(Supplier::get).orElse(null);
            actionIfFalse.accept(context, message);
        }
    }

    /**
     * Asserts that a boolean expression is {@code true}.
     *
     * <pre>{@code
     * Assert.isTrue(i > 0, () -> "The value must be greater than zero");
     * }</pre>
     *
     * @param expression      the boolean expression to check
     * @param messageSupplier a supplier for the exception message to use if the assertion fails (maybe {@code null})
     * @throws IllegalArgumentException if the expression is {@code false}
     * @since 1.0.0
     */
    public static void isTrue(boolean expression, Supplier<String> messageSupplier) {
        if (!expression) {
            throw new IllegalArgumentException(nullSafeGet(messageSupplier));
        }
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * NULL CHECKS
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Asserts that an object is not {@code null}.
     *
     * <pre>{@code
     * Assert.notNull(clazz, "The class must not be null");
     * }</pre>
     *
     * @param object  the object to check
     * @param message the exception message to use if the assertion fails (must not be {@code null})
     * @throws IllegalArgumentException if the object is {@code null}
     * @since 1.0.0
     */
    public static void notNull(Object object, String message) {
        if (object == null) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Asserts that an object is not {@code null}, executing a custom action if it is.
     *
     * <pre>{@code
     * Assert.notNull(obj, "Object is null", () -> context, (ctx, msg) -> {
     *     throw new CustomException(msg);
     * });
     * }</pre>
     *
     * @param <T>             the type of the context object
     * @param object          the object to check
     * @param message         the message to pass to the action (must not be {@code null})
     * @param contextSupplier a supplier for the context object (maybe {@code null})
     * @param actionIfNull    the action to execute if the object is {@code null} (must not be {@code null})
     * @throws NullPointerException if {@code actionIfNull} is {@code null}
     * @since 1.0.0
     */
    public static <T> void notNull(Object object, String message, Supplier<T> contextSupplier, BiConsumer<T, String> actionIfNull) {
        Objects.requireNonNull(actionIfNull, "actionIfNull must not be null");
        if (object == null) {
            T context = Optional.ofNullable(contextSupplier).map(Supplier::get).orElse(null);
            actionIfNull.accept(context, message);
        }
    }

    /**
     * Asserts that an object is not {@code null}.
     *
     * <pre>{@code
     * Assert.notNull(clazz, () -> "The class must not be null");
     * }</pre>
     *
     * @param object          the object to check
     * @param messageSupplier a supplier for the exception message to use if the assertion fails (maybe {@code null})
     * @throws IllegalArgumentException if the object is {@code null}
     * @since 1.0.0
     */
    public static void notNull(Object object, Supplier<String> messageSupplier) {
        if (object == null) {
            throw new IllegalArgumentException(nullSafeGet(messageSupplier));
        }
    }

    /**
     * Asserts that an object is {@code null}.
     *
     * <pre>{@code
     * Assert.isNull(existing, "The value must be null");
     * }</pre>
     *
     * @param object  the object to check
     * @param message the exception message to use if the assertion fails (must not be {@code null})
     * @throws IllegalArgumentException if the object is not {@code null}
     * @since 1.0.0
     */
    public static void isNull(Object object, String message) {
        if (object != null) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Asserts that an object is {@code null}, executing a custom action if it is not.
     *
     * <pre>{@code
     * Assert.isNull(existing, "Already set", () -> ctx, (c, m) -> {
     *     throw new CustomException(m);
     * });
     * }</pre>
     *
     * @param <T>                the type of the context object
     * @param object             the object to check
     * @param message            the message to pass to the action (must not be {@code null})
     * @param contextSupplier    a supplier for the context object (maybe {@code null})
     * @param actionIfNotNull    the action to execute if the object is not {@code null} (must not be {@code null})
     * @throws NullPointerException if {@code actionIfNotNull} is {@code null}
     * @since 1.0.0
     */
    public static <T> void isNull(Object object, String message, Supplier<T> contextSupplier, BiConsumer<T, String> actionIfNotNull) {
        Objects.requireNonNull(actionIfNotNull, "actionIfNotNull must not be null");
        if (object != null) {
            T context = Optional.ofNullable(contextSupplier).map(Supplier::get).orElse(null);
            actionIfNotNull.accept(context, message);
        }
    }

    /**
     * Asserts that an object is {@code null}.
     *
     * <pre>{@code
     * Assert.isNull(existing, () -> "The value must be null");
     * }</pre>
     *
     * @param object          the object to check
     * @param messageSupplier a supplier for the exception message to use if the assertion fails (maybe {@code null})
     * @throws IllegalArgumentException if the object is not {@code null}
     * @since 1.0.0
     */
    public static void isNull(Object object, Supplier<String> messageSupplier) {
        if (object != null) {
            throw new IllegalArgumentException(nullSafeGet(messageSupplier));
        }
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * TEXT CHECKS
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Asserts that the given String contains valid text content.
     * <p>
     * It must not be {@code null} and must contain at least one non-whitespace character.
     * </p>
     *
     * <pre>{@code
     * Assert.hasText(name, "Name must not be empty");
     * }</pre>
     *
     * @param text    the String to check (maybe {@code null})
     * @param message the exception message to use if the assertion fails (must not be {@code null})
     * @throws IllegalArgumentException if the text is null, empty, or contains only whitespace
     * @since 1.0.0
     */
    public static void hasText(String text, String message) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Asserts that the given String contains valid text content, executing a custom action if invalid.
     *
     * <pre>{@code
     * Assert.hasText(name, "Invalid name", () -> ctx, (c, m) -> handleError(c, m));
     * }</pre>
     *
     * @param <T>             the type of the context object
     * @param text            the String to check (maybe {@code null})
     * @param message         the message to pass to the action (must not be {@code null})
     * @param contextSupplier a supplier for the context object (maybe {@code null})
     * @param actionIfInvalid the action to execute if the text is invalid (must not be {@code null})
     * @throws NullPointerException if {@code actionIfInvalid} is {@code null}
     * @since 1.0.0
     */
    public static <T> void hasText(String text, String message, Supplier<T> contextSupplier, BiConsumer<T, String> actionIfInvalid) {
        Objects.requireNonNull(actionIfInvalid, "actionIfInvalid must not be null");
        if (text == null || text.trim().isEmpty()) {
            T context = Optional.ofNullable(contextSupplier).map(Supplier::get).orElse(null);
            actionIfInvalid.accept(context, message);
        }
    }

    /**
     * Asserts that the given String contains valid text content.
     * <p>
     * It must not be {@code null} and must contain at least one non-whitespace character.
     * </p>
     *
     * <pre>{@code
     * Assert.hasText(name, () -> "Name must not be empty");
     * }</pre>
     *
     * @param text            the String to check (maybe {@code null})
     * @param messageSupplier a supplier for the exception message to use if the assertion fails (maybe {@code null})
     * @throws IllegalArgumentException if the text is null, empty, or contains only whitespace
     * @since 1.0.0
     */
    public static void hasText(String text, Supplier<String> messageSupplier) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException(nullSafeGet(messageSupplier));
        }
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * COLLECTION CHECKS
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Asserts that a collection contains elements; that is, it must not be
     * {@code null} and must contain at least one element.
     *
     * <pre>{@code
     * Assert.notEmpty(collection, "Collection must contain elements");
     * }</pre>
     *
     * @param collection the collection to check (maybe {@code null})
     * @param message    the exception message to use if the assertion fails (must not be {@code null})
     * @throws IllegalArgumentException if the collection is {@code null} or empty
     * @since 1.0.0
     */
    public static void notEmpty(Collection<?> collection, String message) {
        if (collection == null || collection.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Asserts that a collection contains elements, executing a custom action if empty.
     *
     * <pre>{@code
     * Assert.notEmpty(list, "Empty list", () -> ctx, (c, m) -> log.warn(m));
     * }</pre>
     *
     * @param <T>             the type of the context object
     * @param collection      the collection to check (maybe {@code null})
     * @param message         the message to pass to the action (must not be {@code null})
     * @param contextSupplier a supplier for the context object (maybe {@code null})
     * @param actionIfEmpty   the action to execute if the collection is empty (must not be {@code null})
     * @throws NullPointerException if {@code actionIfEmpty} is {@code null}
     * @since 1.0.0
     */
    public static <T> void notEmpty(Collection<?> collection, String message, Supplier<T> contextSupplier, BiConsumer<T, String> actionIfEmpty) {
        Objects.requireNonNull(actionIfEmpty, "actionIfEmpty must not be null");
        if (collection == null || collection.isEmpty()) {
            T context = Optional.ofNullable(contextSupplier).map(Supplier::get).orElse(null);
            actionIfEmpty.accept(context, message);
        }
    }

    /**
     * Asserts that a collection contains elements; that is, it must not be
     * {@code null} and must contain at least one element.
     *
     * <pre>{@code
     * Assert.notEmpty(collection, () -> "Collection must contain elements");
     * }</pre>
     *
     * @param collection      the collection to check (maybe {@code null})
     * @param messageSupplier a supplier for the exception message to use if the assertion fails (maybe {@code null})
     * @throws IllegalArgumentException if the collection is {@code null} or empty
     * @since 1.0.0
     */
    public static void notEmpty(Collection<?> collection, Supplier<String> messageSupplier) {
        if (collection == null || collection.isEmpty()) {
            throw new IllegalArgumentException(nullSafeGet(messageSupplier));
        }
    }

    /**
     * Asserts that a map contains entries; that is, it must not be {@code null}
     * and must contain at least one entry.
     *
     * <pre>{@code
     * Assert.notEmpty(map, "Map must contain entries");
     * }</pre>
     *
     * @param map     the map to check (maybe {@code null})
     * @param message the exception message to use if the assertion fails (must not be {@code null})
     * @throws IllegalArgumentException if the map is {@code null} or empty
     * @since 1.0.0
     */
    public static void notEmpty(Map<?, ?> map, String message) {
        if (map == null || map.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Asserts that a map contains entries, executing a custom action if empty.
     *
     * <pre>{@code
     * Assert.notEmpty(map, "Empty map", () -> ctx, (c, m) -> throw new Error(m));
     * }</pre>
     *
     * @param <T>             the type of the context object
     * @param map             the map to check (maybe {@code null})
     * @param message         the message to pass to the action (must not be {@code null})
     * @param contextSupplier a supplier for the context object (maybe {@code null})
     * @param actionIfEmpty   the action to execute if the map is empty (must not be {@code null})
     * @throws NullPointerException if {@code actionIfEmpty} is {@code null}
     * @since 1.0.0
     */
    public static <T> void notEmpty(Map<?, ?> map, String message, Supplier<T> contextSupplier, BiConsumer<T, String> actionIfEmpty) {
        Objects.requireNonNull(actionIfEmpty, "actionIfEmpty must not be null");
        if (map == null || map.isEmpty()) {
            T context = Optional.ofNullable(contextSupplier).map(Supplier::get).orElse(null);
            actionIfEmpty.accept(context, message);
        }
    }

    /**
     * Asserts that a map contains entries; that is, it must not be {@code null}
     * and must contain at least one entry.
     *
     * <pre>{@code
     * Assert.notEmpty(map, () -> "Map must contain entries");
     * }</pre>
     *
     * @param map             the map to check (maybe {@code null})
     * @param messageSupplier a supplier for the exception message to use if the assertion fails (maybe {@code null})
     * @throws IllegalArgumentException if the map is {@code null} or empty
     * @since 1.0.0
     */
    public static void notEmpty(Map<?, ?> map, Supplier<String> messageSupplier) {
        if (map == null || map.isEmpty()) {
            throw new IllegalArgumentException(nullSafeGet(messageSupplier));
        }
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * INTERNAL HELPERS
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Helper to safely get a message from a supplier.
     */
    private static String nullSafeGet(Supplier<String> messageSupplier) {
        return (messageSupplier != null ? messageSupplier.get() : null);
    }
}
