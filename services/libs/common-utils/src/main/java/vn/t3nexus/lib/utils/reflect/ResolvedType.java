/*
 * Created by Truong Ngo (2026).
 */
package vn.t3nexus.lib.utils.reflect;

import vn.t3nexus.lib.utils.lang.Assert;

import java.lang.reflect.*;
import java.util.Collection;
import java.util.Map;

/**
 * Lightweight representation of a resolved Java generic type.
 * <p>
 * Covers common parameterized type navigation without external dependencies.
 * Use factory methods to obtain an instance from a field, method, or class.
 * </p>
 *
 * <p><b>Supported:</b></p>
 * <ul>
 * <li>Simple parameterized types: {@code List<String>}, {@code Map<String, Integer>}</li>
 * <li>Nested navigation: {@code Map<String, List<Integer>>} via {@code getGeneric(1, 0)}</li>
 * <li>Single-level {@code TypeVariable} resolution via implementation class context</li>
 * </ul>
 *
 * <p><b>Not supported — use Spring's {@code ResolvableType} for:</b></p>
 * <ul>
 * <li>Wildcard assignability ({@code ? extends Number})</li>
 * <li>Deep multi-level {@code TypeVariable} rebinding across complex hierarchies</li>
 * <li>Bridge method resolution</li>
 * </ul>
 *
 * @author Truong Ngo
 * @version 1.0.0
 * @since 1.0.0
 * @see GenericUtils
 */
public final class ResolvedType {

    /** Sentinel representing an unresolvable or absent type. */
    public static final ResolvedType NONE = new ResolvedType(null, null);

    private static final ResolvedType[] EMPTY_ARRAY = new ResolvedType[0];

    private final Type type;
    private final Class<?> context;

    private ResolvedType(Type type, Class<?> context) {
        this.type = type;
        this.context = context;
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * FACTORY
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Creates a {@code ResolvedType} for the given field in the context of {@code context}.
     *
     * @param field   the field to resolve (must not be {@code null})
     * @param context the implementation class providing TypeVariable bindings (maybe {@code null})
     * @return the resolved type
     */
    public static ResolvedType forField(Field field, Class<?> context) {
        Assert.notNull(field, "Field must not be null");
        return new ResolvedType(field.getGenericType(), context);
    }

    /**
     * Creates a {@code ResolvedType} for the return type of the given method.
     *
     * @param method  the method to resolve (must not be {@code null})
     * @param context the implementation class providing TypeVariable bindings (maybe {@code null})
     * @return the resolved type
     */
    public static ResolvedType forMethodReturn(Method method, Class<?> context) {
        Assert.notNull(method, "Method must not be null");
        return new ResolvedType(method.getGenericReturnType(), context);
    }

    /**
     * Creates a {@code ResolvedType} for a parameter of the given method.
     *
     * @param method  the method to resolve (must not be {@code null})
     * @param index   the parameter index (0-based)
     * @param context the implementation class providing TypeVariable bindings (maybe {@code null})
     * @return the resolved type, or {@link #NONE} if the index is out of bounds
     */
    public static ResolvedType forMethodParameter(Method method, int index, Class<?> context) {
        Assert.notNull(method, "Method must not be null");
        Type[] params = method.getGenericParameterTypes();
        if (index < 0 || index >= params.length) return NONE;
        return new ResolvedType(params[index], context);
    }

    /**
     * Creates a {@code ResolvedType} from a raw {@link Type} and a context class.
     *
     * @param type    the type to wrap (must not be {@code null})
     * @param context the implementation class providing TypeVariable bindings (maybe {@code null})
     * @return the resolved type
     */
    public static ResolvedType of(Type type, Class<?> context) {
        Assert.notNull(type, "Type must not be null");
        return new ResolvedType(type, context);
    }

    /**
     * Creates a {@code ResolvedType} from a plain class (no generic context needed).
     *
     * @param clazz the class to wrap (must not be {@code null})
     * @return the resolved type
     */
    public static ResolvedType of(Class<?> clazz) {
        Assert.notNull(clazz, "Class must not be null");
        return new ResolvedType(clazz, clazz);
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * RESOLUTION
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Returns {@code true} if this is the {@link #NONE} sentinel.
     */
    public boolean isNone() {
        return this == NONE;
    }

    /**
     * Resolves this type to its raw {@link Class}.
     * Returns {@code Object.class} for {@link #NONE} or unresolvable types.
     */
    public Class<?> resolve() {
        if (isNone()) return Object.class;
        return GenericUtils.resolveTypeToClass(type, context);
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * GENERIC NAVIGATION
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Returns {@code true} if this type has resolvable generic type arguments.
     */
    public boolean hasGenerics() {
        if (isNone()) return false;
        return effectiveType() instanceof ParameterizedType pt
                && pt.getActualTypeArguments().length > 0;
    }

    /**
     * Returns all generic type arguments as {@code ResolvedType} instances.
     * Returns an empty array if this type has no generics.
     */
    public ResolvedType[] getGenerics() {
        if (isNone()) return EMPTY_ARRAY;
        if (!(effectiveType() instanceof ParameterizedType pt)) return EMPTY_ARRAY;
        Type[] args = pt.getActualTypeArguments();
        ResolvedType[] result = new ResolvedType[args.length];
        for (int i = 0; i < args.length; i++) {
            result[i] = new ResolvedType(args[i], context);
        }
        return result;
    }

    /**
     * Navigates into nested generic type arguments by a chain of indexes.
     *
     * <pre>{@code
     * // Field: Map<String, List<Integer>>
     * ResolvedType.forField(field, ctx)
     *     .getGeneric(1, 0)   // navigate to List<Integer>, then Integer
     *     .resolve()          // → Integer.class
     * }</pre>
     *
     * @param indexes the chain of zero-based indexes to navigate
     * @return the nested {@code ResolvedType}, or {@link #NONE} if not found
     */
    public ResolvedType getGeneric(int... indexes) {
        if (isNone() || indexes == null || indexes.length == 0) return this;
        ResolvedType current = this;
        for (int index : indexes) {
            current = current.getGenericAt(index);
            if (current.isNone()) return NONE;
        }
        return current;
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * TYPE CHECKS
     * -----------------------------------------------------------------------------------------------------------------
     */

    /** Returns {@code true} if this type is an array type. */
    public boolean isArray() {
        if (isNone()) return false;
        return type instanceof GenericArrayType
                || (type instanceof Class<?> c && c.isArray());
    }

    /** Returns {@code true} if this type is assignable to {@link Collection}. */
    public boolean isCollection() {
        if (isNone()) return false;
        return Collection.class.isAssignableFrom(resolve());
    }

    /** Returns {@code true} if this type is assignable to {@link Map}. */
    public boolean isMap() {
        if (isNone()) return false;
        return Map.class.isAssignableFrom(resolve());
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * HELPERS
     * -----------------------------------------------------------------------------------------------------------------
     */

    private ResolvedType getGenericAt(int index) {
        if (!(effectiveType() instanceof ParameterizedType pt)) return NONE;
        Type[] args = pt.getActualTypeArguments();
        if (index < 0 || index >= args.length) return NONE;
        return new ResolvedType(args[index], context);
    }

    /** Resolves TypeVariable to its concrete bound before further inspection. */
    private Type effectiveType() {
        if (type instanceof TypeVariable<?>) {
            return resolve();
        }
        return type;
    }

    @Override
    public String toString() {
        if (isNone()) return "ResolvedType[NONE]";
        return "ResolvedType[" + type.getTypeName() + "]";
    }
}
