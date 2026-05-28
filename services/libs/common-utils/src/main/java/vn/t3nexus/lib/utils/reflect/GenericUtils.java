/*
 * Created by Truong Ngo (2026).
 */
package vn.t3nexus.lib.utils.reflect;

import vn.t3nexus.lib.utils.lang.Assert;

import java.lang.reflect.*;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Utility for resolving Java Generic Types (Type Erasure handling).
 * <p>
 * This class provides utilities to resolve actual type arguments from class hierarchies,
 * decode generic fields and methods, and handle array component types. It uses caching to improve performance.
 * </p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 * <li><b>Type Discovery:</b> Resolve actual type arguments by scanning the class hierarchy (Superclasses & Interfaces).</li>
 * <li><b>Resolution:</b> Decode actual types for fields and methods involving generics.</li>
 * <li><b>Integrity:</b> Check assignability and handle array component types.</li>
 * </ul>
 *
 * <p><b>High-level API:</b> The {@code resolveFieldType} / {@code resolveMethodReturnType} /
 * {@code resolveMethodParameterType} methods return a raw {@link Class} — all nested generic
 * structure is lost. When you need to navigate type arguments (e.g. the element type of a
 * {@code List<String>} field), use the {@link ResolvedType} entry points instead:
 * {@link #forField}, {@link #forMethodReturn}, {@link #forMethodParameter}, {@link #of}.</p>
 *
 * <p>This class is thread-safe and intended for static use.</p>
 *
 * @author Truong Ngo
 * @version 1.2.2
 * @since 1.0.0
 * @see ResolvedType
 */
public final class GenericUtils {

    private record TypeCacheKey(Class<?> clazz, Class<?> genericType, int index) {}

    private static final Map<TypeCacheKey, Class<?>> typeCache = Collections.synchronizedMap(new WeakHashMap<>(256));

    /**
     * Prevents instantiation of this utility class.
     * @throws UnsupportedOperationException always
     */
    private GenericUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * TYPE DISCOVERY
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Resolves the actual class of a generic type argument by scanning the entire hierarchy.
     * <p>
     * This method traverses both superclasses and interfaces to find the concrete type
     * bound to the specified generic parameter index.
     * </p>
     *
     * <pre>{@code
     * // Example 1: Direct Inheritance
     * // class StringList extends ArrayList<String> {}
     * Class<?> type = GenericUtils.resolveActualTypeArgument(StringList.class, ArrayList.class, 0);
     * // Result: String.class
     *
     * // Example 2: Nested Hierarchy
     * // interface Service<T> {}
     * // abstract class BaseService<T> implements Service<T> {}
     * // class UserService extends BaseService<User> {}
     * Class<?> type = GenericUtils.resolveActualTypeArgument(UserService.class, Service.class, 0);
     * // Result: User.class
     * }</pre>
     *
     * @param clazz       the implementation class to start scanning from (must not be {@code null})
     * @param genericType the generic class or interface to resolve arguments for (must not be {@code null})
     * @param index       the index of the type argument (0-based)
     * @return the resolved class, or {@code Object.class} if not found
     * @since 1.0.0
     */
    public static Class<?> resolveActualTypeArgument(Class<?> clazz, Class<?> genericType, int index) {
        Assert.notNull(clazz, "Source class must not be null");
        Assert.notNull(genericType, "Generic type to resolve must not be null");

        Class<?> userClass = ClassUtils.getUserClass(clazz);

        // Check assignability before scanning
        if (!genericType.isAssignableFrom(userClass)) {
            return Object.class;
        }

        TypeCacheKey cacheKey = new TypeCacheKey(userClass, genericType, index);

        return typeCache.computeIfAbsent(cacheKey, key -> {
            Type resolved = internalResolve(userClass, genericType, index);
            return getRawClass(resolved);
        });
    }

    /**
     * Core recursive logic: Scans both vertical (Superclass) and horizontal (Interfaces) axes.
     */
    private static Type internalResolve(Class<?> clazz, Class<?> target, int index) {
        if (clazz == null || clazz == Object.class) return null;

        // Stop if we reached the target itself
        if (clazz == target) return null;

        // 1. Scan horizontal axis (Interfaces)
        for (Type ifc : clazz.getGenericInterfaces()) {
            Type result = extractFromType(ifc, target, index);
            if (result != null) return result;

            Class<?> rawIfc = getRawClass(ifc);
            if (rawIfc != null && target.isAssignableFrom(rawIfc)) {
                result = internalResolve(rawIfc, target, index);
                if (result != null) return result;
            }
        }

        // 2. Scan vertical axis (Superclass)
        Type superType = clazz.getGenericSuperclass();
        if (superType != null) {
            Type result = extractFromType(superType, target, index);
            if (result != null) return result;
        }

        return internalResolve(clazz.getSuperclass(), target, index);
    }

    private static Type extractFromType(Type type, Class<?> target, int index) {
        if (type instanceof ParameterizedType pt) {
            if (pt.getRawType() == target) {
                Type[] args = pt.getActualTypeArguments();
                return (index >= 0 && index < args.length) ? args[index] : null;
            }
        }
        return null;
    }

    /**
     * Extracts the raw class from a Type.
     * <p>
     * Handles Class, ParameterizedType, GenericArrayType, TypeVariable, and WildcardType.
     * </p>
     *
     * <pre>{@code
     * Class<?> raw = GenericUtils.getRawClass(myListType); // List.class
     * }</pre>
     *
     * @param type the type to extract from (maybe {@code null})
     * @return the raw class, or {@code Object.class} if unknown
     * @since 1.0.0
     */
    public static Class<?> getRawClass(Type type) {
        if (type == null) return Object.class;
        if (type instanceof Class<?> c) return c;
        if (type instanceof ParameterizedType pt) return (Class<?>) pt.getRawType();

        if (type instanceof GenericArrayType gat) {
            Class<?> componentClass = getRawClass(gat.getGenericComponentType());
            return Array.newInstance(componentClass, 0).getClass();
        }

        if (type instanceof TypeVariable<?> tv) {
            Type[] bounds = tv.getBounds();
            return (bounds.length > 0) ? getRawClass(bounds[0]) : Object.class;
        }

        if (type instanceof WildcardType wt) {
            Type[] bounds = wt.getUpperBounds();
            return (bounds.length > 0) ? getRawClass(bounds[0]) : Object.class;
        }

        return Object.class;
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * RESOLUTION
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Resolves the actual type of a field.
     * <p>
     * Handles cases where the field is declared as a generic type (e.g., {@code T data}).
     * </p>
     *
     * <pre>{@code
     * // class Box<T> { T data; }
     * // class StringBox extends Box<String> {}
     * Field field = Box.class.getDeclaredField("data");
     * Class<?> type = GenericUtils.resolveFieldType(field, StringBox.class);
     * // Result: String.class
     * }</pre>
     *
     * @param field               the field to resolve (must not be {@code null})
     * @param implementationClass the concrete class containing the field context
     * @return the resolved class of the field
     * @see #forField(Field, Class) for richer navigation over generic parameters
     * @since 1.0.0
     */
    public static Class<?> resolveFieldType(Field field, Class<?> implementationClass) {
        Assert.notNull(field, "Field must not be null");
        return resolveTypeToClass(field.getGenericType(), implementationClass);
    }

    /**
     * Resolves the actual type of a method parameter.
     *
     * <pre>{@code
     * // interface Processor<T> { void process(T item); }
     * // class StringProcessor implements Processor<String> {}
     * Method method = Processor.class.getMethod("process", Object.class);
     * Class<?> type = GenericUtils.resolveMethodParameterType(method, 0, StringProcessor.class);
     * // Result: String.class
     * }</pre>
     *
     * @param method              the method to resolve (must not be {@code null})
     * @param paramIndex          the index of the parameter (0-based)
     * @param implementationClass the concrete class containing the method context
     * @return the resolved class of the parameter
     * @see #forMethodParameter(Method, int, Class) for richer navigation over generic parameters
     * @since 1.0.0
     */
    public static Class<?> resolveMethodParameterType(Method method, int paramIndex, Class<?> implementationClass) {
        Assert.notNull(method, "Method must not be null");
        Type[] parameterTypes = method.getGenericParameterTypes();
        if (paramIndex >= parameterTypes.length) return Object.class;
        return resolveTypeToClass(parameterTypes[paramIndex], implementationClass);
    }

    /**
     * Resolves the actual return type of a method.
     *
     * <pre>{@code
     * // interface Factory<T> { T create(); }
     * // class IntegerFactory implements Factory<Integer> {}
     * Method method = Factory.class.getMethod("create");
     * Class<?> type = GenericUtils.resolveMethodReturnType(method, IntegerFactory.class);
     * // Result: Integer.class
     * }</pre>
     *
     * @param method              the method to resolve (must not be {@code null})
     * @param implementationClass the concrete class containing the method context
     * @return the resolved return class
     * @see #forMethodReturn(Method, Class) for richer navigation over generic parameters
     * @since 1.0.0
     */
    public static Class<?> resolveMethodReturnType(Method method, Class<?> implementationClass) {
        Assert.notNull(method, "Method must not be null");
        return resolveTypeToClass(method.getGenericReturnType(), implementationClass);
    }

    /**
     * Resolves a generic Type to a concrete Class within the context of an implementation class.
     *
     * @param type                the type to resolve (maybe {@code null})
     * @param implementationClass the context class
     * @return the resolved class
     * @since 1.0.0
     */
    public static Class<?> resolveTypeToClass(Type type, Class<?> implementationClass) {
        if (type == null) return Object.class;

        // 1. Direct Class
        if (type instanceof Class<?> clazz) return clazz;

        // 2. Parameterized (Wrapper)
        if (type instanceof ParameterizedType pt) return (Class<?>) pt.getRawType();

        // 3. TypeVariable (Context matching)
        if (type instanceof TypeVariable<?> tv) {
            GenericDeclaration declaration = tv.getGenericDeclaration();
            if (declaration instanceof Class<?> declaringClass) {
                TypeVariable<?>[] typeParams = declaringClass.getTypeParameters();
                for (int i = 0; i < typeParams.length; i++) {
                    if (typeParams[i].getName().equals(tv.getName())) {
                        return resolveActualTypeArgument(implementationClass, declaringClass, i);
                    }
                }
            }
            return getRawClass(tv);
        }

        // 4. Generic Array or Wildcard
        return getRawClass(type);
    }

    /**
     * Extracts a generic parameter type from a parameterized type by index.
     *
     * <pre>{@code
     * // List<String> -> getGenericParameterType(type, 0) -> String.class
     * }</pre>
     *
     * @param type  the parameterized type
     * @param index the index of the argument
     * @return the type argument, or {@code Object.class} if not found
     * @since 1.0.0
     */
    public static Type getGenericParameterType(Type type, int index) {
        if (type instanceof ParameterizedType pt) {
            Type[] args = pt.getActualTypeArguments();
            return (index >= 0 && index < args.length) ? args[index] : Object.class;
        }
        return Object.class;
    }

    /**
     * Resolves the component type of an array type.
     * <p>
     * Supports generic arrays (e.g., {@code T[]}).
     * </p>
     *
     * <pre>{@code
     * Class<?> comp = GenericUtils.getArrayComponentType(myGenericArrayType);
     * }</pre>
     *
     * @param type the type to check (maybe {@code null})
     * @return the component class, or {@code null} if not an array
     * @since 1.0.0
     */
    public static Class<?> getArrayComponentType(Type type) {
        if (type instanceof GenericArrayType gat) {
            return getRawClass(gat.getGenericComponentType());
        }
        if (type instanceof Class<?> clazz && clazz.isArray()) {
            return clazz.getComponentType();
        }
        return null;
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * INTEGRITY & UTILS
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Checks if the source type is assignable to the target type.
     * <p>
     * This is an extended compatibility check for Generics.
     * </p>
     *
     * <pre>{@code
     * boolean valid = GenericUtils.isAssignable(Number.class, Integer.class);
     * }</pre>
     *
     * @param target the target type (maybe {@code null})
     * @param source the source type (maybe {@code null})
     * @return {@code true} if assignable
     * @since 1.0.0
     */
    public static boolean isAssignable(Type target, Type source) {
        if (target instanceof Class<?> t && source instanceof Class<?> s) {
            return ClassUtils.isAssignable(t, s);
        }
        return false;
    }

    /**
     * Clears the internal type cache.
     *
     * @since 1.0.0
     */
    public static void clearCache() {
        typeCache.clear();
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * RESOLVED TYPE — high-level navigation API
     * These methods return a {@link ResolvedType} that preserves full generic structure,
     * enabling navigation of nested type arguments (e.g. the element type of List<String>).
     * Use them instead of the resolveXxx methods whenever you need more than a raw Class.
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Returns a {@link ResolvedType} for the given field.
     * <p>Use this instead of {@link #resolveFieldType} when you need to navigate generic parameters:</p>
     * <pre>{@code
     * // Field: Map<String, List<Integer>>
     * ResolvedType rt = GenericUtils.forField(field, ctx);
     * Class<?> valueElement = rt.getGeneric(1, 0).resolve(); // Integer.class
     * }</pre>
     *
     * @param field   the field to resolve (must not be {@code null})
     * @param context the implementation class providing TypeVariable bindings (maybe {@code null})
     * @return the resolved type
     * @since 1.0.0
     */
    public static ResolvedType forField(Field field, Class<?> context) {
        return ResolvedType.forField(field, context);
    }

    /**
     * Returns a {@link ResolvedType} for the return type of the given method.
     * <p>Use this instead of {@link #resolveMethodReturnType} when you need to navigate generic parameters.</p>
     *
     * @param method  the method to resolve (must not be {@code null})
     * @param context the implementation class providing TypeVariable bindings (maybe {@code null})
     * @return the resolved type
     * @since 1.0.0
     */
    public static ResolvedType forMethodReturn(Method method, Class<?> context) {
        return ResolvedType.forMethodReturn(method, context);
    }

    /**
     * Returns a {@link ResolvedType} for a parameter of the given method.
     * <p>Use this instead of {@link #resolveMethodParameterType} when you need to navigate generic parameters.</p>
     *
     * @param method  the method to resolve (must not be {@code null})
     * @param index   the parameter index (0-based)
     * @param context the implementation class providing TypeVariable bindings (maybe {@code null})
     * @return the resolved type, or {@link ResolvedType#NONE} if the index is out of bounds
     * @since 1.0.0
     */
    public static ResolvedType forMethodParameter(Method method, int index, Class<?> context) {
        return ResolvedType.forMethodParameter(method, index, context);
    }

    /**
     * Wraps a raw {@link Type} and context in a {@link ResolvedType} for navigation.
     *
     * @param type    the type to wrap (must not be {@code null})
     * @param context the implementation class providing TypeVariable bindings (maybe {@code null})
     * @return the resolved type
     * @since 1.0.0
     */
    public static ResolvedType of(Type type, Class<?> context) {
        return ResolvedType.of(type, context);
    }
}
