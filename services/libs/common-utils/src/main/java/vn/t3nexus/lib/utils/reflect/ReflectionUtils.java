/*
 * Created by Truong Ngo (2026).
 */
package vn.t3nexus.lib.utils.reflect;

import vn.t3nexus.lib.utils.lang.Assert;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * High-performance reflection utilities with caching and enhanced safety.
 * <p>
 * This class provides a comprehensive set of utilities for working with Java Reflection API.
 * It includes caching mechanisms for Fields, Methods, and Constructors to improve performance,
 * as well as helper methods for finding, invoking, and manipulating class members.
 * </p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 * <li><b>Discovery:</b> Find fields, methods, and constructors with flexible criteria.</li>
 * <li><b>Traversal:</b> Iterate over class structures with callbacks (Functional style).</li>
 * <li><b>Invocation:</b> Safe execution of methods and constructors.</li>
 * <li><b>Accessibility:</b> Utilities to handle {@code setAccessible(true)} safely.</li>
 * <li><b>Caching:</b> Internal caching to minimize reflection overhead.</li>
 * </ul>
 *
 * <p>This class is thread-safe and intended for static use.</p>
 *
 * @author Truong Ngo
 * @version 1.0.0
 * @since 1.0.0
 */
public final class ReflectionUtils {

    /**
     * Key for caching methods based on name and parameter types.
     */
    public static final class MethodKey {
        private final String name;
        private final Class<?>[] params;

        public MethodKey(Method method) {
            this.name = method.getName();
            this.params = method.getParameterTypes();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MethodKey that = (MethodKey) o;
            return name.equals(that.name) && Arrays.equals(params, that.params);
        }

        @Override
        public int hashCode() {
            int result = name.hashCode();
            result = 31 * result + Arrays.hashCode(params);
            return result;
        }
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * CACHE & CONSTANTS
     * -----------------------------------------------------------------------------------------------------------------
     */

    // Cache storage for Methods, Fields, and Constructors using WeakReference keys (Class)
    private static final Map<Class<?>, Method[]> methodCache = Collections.synchronizedMap(new WeakHashMap<>(256));
    private static final Map<Class<?>, Field[]> fieldCache = Collections.synchronizedMap(new WeakHashMap<>(256));
    private static final Map<Class<?>, Constructor<?>[]> constructorCache = Collections.synchronizedMap(new WeakHashMap<>(256));

    private static final Method[] EMPTY_METHOD_ARRAY = new Method[0];
    private static final Field[] EMPTY_FIELD_ARRAY = new Field[0];

    /**
     * Prevents instantiation of this utility class.
     * @throws UnsupportedOperationException always
     */
    private ReflectionUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * DISCOVERY
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Retrieves all declared fields of the given class.
     *
     * @param clazz the class to introspect (maybe {@code null})
     * @return an array of fields, or an empty array if none found
     * @since 1.0.0
     */
    public static Field[] findDeclaredFields(Class<?> clazz) {
        return getDeclaredFields(clazz);
    }

    /**
     * Retrieves all declared fields of the given class that match the predicate.
     *
     * @param clazz     the class to introspect (maybe {@code null})
     * @param predicate the filter condition (must not be {@code null})
     * @return an array of matching fields
     * @since 1.0.0
     */
    public static Field[] findDeclaredFields(Class<?> clazz, Predicate<Field> predicate) {
        return Arrays.stream(getDeclaredFields(clazz))
                .filter(predicate)
                .toArray(Field[]::new);
    }

    /**
     * Finds a declared field by name in the given class.
     *
     * @param clazz the class to introspect (maybe {@code null})
     * @param name  the name of the field (must not be {@code null})
     * @return the field, or {@code null} if not found
     * @since 1.0.0
     */
    public static Field findDeclaredField(Class<?> clazz, String name) {
        return findDeclaredField(clazz, f -> f.getName().equals(name));
    }

    /**
     * Finds a declared field by name and type in the given class.
     *
     * @param clazz the class to introspect (maybe {@code null})
     * @param name  the name of the field (must not be {@code null})
     * @param type  the type of the field (maybe {@code null} to ignore type check)
     * @return the field, or {@code null} if not found
     * @since 1.0.0
     */
    public static Field findDeclaredField(Class<?> clazz, String name, Class<?> type) {
        return findDeclaredField(clazz, f ->
                f.getName().equals(name) && (type == null || f.getType().equals(type)));
    }

    /**
     * Finds a single declared field matching the predicate.
     *
     * @param clazz     the class to introspect (maybe {@code null})
     * @param predicate the filter condition (must not be {@code null})
     * @return the first matching field, or {@code null}
     * @since 1.0.0
     */
    public static Field findDeclaredField(Class<?> clazz, Predicate<Field> predicate) {
        for (Field field : getDeclaredFields(clazz)) {
            if (predicate.test(field)) return field;
        }
        return null;
    }

    /**
     * Retrieves all fields of the given class, including inherited ones.
     *
     * @param clazz the class to introspect (maybe {@code null})
     * @return an array of all fields
     * @since 1.0.0
     */
    public static Field[] findFields(Class<?> clazz) {
        if (clazz == null || clazz == Object.class) return EMPTY_FIELD_ARRAY;

        Map<String, Field> fieldMap = new LinkedHashMap<>();

        // Load order: Current -> Superclasses -> Interfaces
        fillFields(clazz, fieldMap);
        ClassUtils.getAllSuperclasses(clazz).forEach(c -> fillFields(c, fieldMap));
        ClassUtils.getAllInterfacesForClassAsSet(clazz).forEach(i -> fillFields(i, fieldMap));

        return fieldMap.values().toArray(new Field[0]);
    }

    /**
     * Retrieves all fields of the given class (including inherited) that match the predicate.
     *
     * @param clazz     the class to introspect (maybe {@code null})
     * @param predicate the filter condition (must not be {@code null})
     * @return an array of matching fields
     * @since 1.0.0
     */
    public static Field[] findFields(Class<?> clazz, Predicate<Field> predicate) {
        return Arrays.stream(findFields(clazz))
                .filter(predicate)
                .toArray(Field[]::new);
    }

    /**
     * Finds a field by name in the given class or its hierarchy.
     *
     * @param clazz the class to introspect (maybe {@code null})
     * @param name  the name of the field (must not be {@code null})
     * @return the field, or {@code null} if not found
     * @since 1.0.0
     */
    public static Field findField(Class<?> clazz, String name) {
        return findField(clazz, name, null);
    }

    /**
     * Finds a field by name and type in the given class or its hierarchy.
     *
     * @param clazz the class to introspect (maybe {@code null})
     * @param name  the name of the field (must not be {@code null})
     * @param type  the type of the field (maybe {@code null})
     * @return the field, or {@code null} if not found
     * @since 1.0.0
     */
    public static Field findField(Class<?> clazz, String name, Class<?> type) {
        return findField(clazz, f ->
                f.getName().equals(name) && (type == null || f.getType().equals(type)));
    }

    /**
     * Finds a single field matching the predicate in the class hierarchy.
     *
     * @param clazz     the class to introspect (maybe {@code null})
     * @param predicate the filter condition (must not be {@code null})
     * @return the first matching field, or {@code null}
     * @since 1.0.0
     */
    public static Field findField(Class<?> clazz, Predicate<Field> predicate) {
        if (clazz == null || clazz == Object.class) return null;

        // 1. Find in the class itself
        Field field = findDeclaredField(clazz, predicate);
        if (field != null) return field;

        // 2. Find in superclasses
        for (Class<?> superClazz : ClassUtils.getAllSuperclasses(clazz)) {
            Field f = findDeclaredField(superClazz, predicate);
            if (f != null) return f;
        }

        // 3. Find in interfaces (static final constants)
        for (Class<?> ifc : ClassUtils.getAllInterfacesForClassAsSet(clazz)) {
            Field f = findDeclaredField(ifc, predicate);
            if (f != null) return f;
        }
        return null;
    }

    /**
     * Retrieves all declared methods of the given class.
     *
     * @param clazz the class to introspect (maybe {@code null})
     * @return an array of methods
     * @since 1.0.0
     */
    public static Method[] findDeclaredMethods(Class<?> clazz) {
        return getDeclaredMethods(clazz);
    }

    /**
     * Retrieves all declared methods matching the predicate.
     *
     * @param clazz     the class to introspect (maybe {@code null})
     * @param predicate the filter condition (must not be {@code null})
     * @return an array of matching methods
     * @since 1.0.0
     */
    public static Method[] findDeclaredMethods(Class<?> clazz, Predicate<Method> predicate) {
        return Arrays.stream(getDeclaredMethods(clazz)).filter(predicate).toArray(Method[]::new);
    }

    /**
     * Finds a declared method by name and parameter types.
     *
     * @param clazz      the class to introspect (maybe {@code null})
     * @param name       the name of the method (must not be {@code null})
     * @param paramTypes the parameter types (maybe {@code null})
     * @return the method, or {@code null} if not found
     * @since 1.0.0
     */
    public static Method findDeclaredMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        return findDeclaredMethod(clazz, m ->
                m.getName().equals(name) && Arrays.equals(m.getParameterTypes(), paramTypes));
    }

    /**
     * Finds a single declared method matching the predicate.
     *
     * @param clazz     the class to introspect (maybe {@code null})
     * @param predicate the filter condition (must not be {@code null})
     * @return the method, or {@code null}
     * @since 1.0.0
     */
    public static Method findDeclaredMethod(Class<?> clazz, Predicate<Method> predicate) {
        for (Method method : getDeclaredMethods(clazz)) {
            if (predicate.test(method)) return method;
        }
        return null;
    }

    /**
     * Retrieves all methods of the given class, including inherited ones.
     *
     * @param clazz the class to introspect (maybe {@code null})
     * @return an array of all methods
     * @since 1.0.0
     */
    public static Method[] findMethods(Class<?> clazz) {
        if (clazz == null || clazz == Object.class) return EMPTY_METHOD_ARRAY;

        // Use LinkedHashMap to ensure order and handle Overrides
        Map<MethodKey, Method> methodMap = new LinkedHashMap<>();

        // 1. Current Class
        fillMethods(clazz, methodMap);

        // 2. Superclasses
        ClassUtils.getAllSuperclasses(clazz).forEach(c -> fillMethods(c, methodMap));

        // 3. Interfaces (Including default methods)
        ClassUtils.getAllInterfacesForClassAsSet(clazz).forEach(i -> fillMethods(i, methodMap));

        return methodMap.values().toArray(new Method[0]);
    }

    /**
     * Retrieves all methods of the given class (including inherited) that match the predicate.
     *
     * @param clazz     the class to introspect (maybe {@code null})
     * @param predicate the filter condition (must not be {@code null})
     * @return an array of matching methods
     * @since 1.0.0
     */
    public static Method[] findMethods(Class<?> clazz, Predicate<Method> predicate) {
        return Arrays.stream(findMethods(clazz)).filter(predicate).toArray(Method[]::new);
    }

    /**
     * Finds a method by name and parameter types in the class hierarchy.
     *
     * @param clazz      the class to introspect (maybe {@code null})
     * @param name       the name of the method (must not be {@code null})
     * @param paramTypes the parameter types (maybe {@code null})
     * @return the method, or {@code null} if not found
     * @since 1.0.0
     */
    public static Method findMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        return findMethod(clazz, m ->
                m.getName().equals(name) && Arrays.equals(m.getParameterTypes(), paramTypes));
    }

    /**
     * Finds a single method matching the predicate in the class hierarchy.
     *
     * @param clazz     the class to introspect (maybe {@code null})
     * @param predicate the filter condition (must not be {@code null})
     * @return the method, or {@code null}
     * @since 1.0.0
     */
    public static Method findMethod(Class<?> clazz, Predicate<Method> predicate) {
        if (clazz == null || clazz == Object.class) return null;

        Method method = findDeclaredMethod(clazz, predicate);
        if (method != null) return method;

        for (Class<?> superClazz : ClassUtils.getAllSuperclasses(clazz)) {
            Method m = findDeclaredMethod(superClazz, predicate);
            if (m != null) return m;
        }

        for (Class<?> ifc : ClassUtils.getAllInterfacesForClassAsSet(clazz)) {
            Method m = findDeclaredMethod(ifc, predicate);
            if (m != null) return m;
        }
        return null;
    }

    /**
     * Retrieves all declared constructors of the given class.
     *
     * @param <T>   the type of the class
     * @param clazz the class to introspect (maybe {@code null})
     * @return an array of constructors
     * @since 1.0.0
     */
    public static <T> Constructor<T>[] getConstructors(Class<T> clazz) {
        return getDeclaredConstructors(clazz);
    }

    /**
     * Retrieves all declared constructors matching the predicate.
     *
     * @param <T>       the type of the class
     * @param clazz     the class to introspect (maybe {@code null})
     * @param predicate the filter condition (must not be {@code null})
     * @return an array of matching constructors
     * @since 1.0.0
     */
    @SuppressWarnings("unchecked")
    public static <T> Constructor<T>[] getConstructors(Class<T> clazz, Predicate<Constructor<T>> predicate) {
        return Arrays.stream(getDeclaredConstructors(clazz))
                .filter(predicate)
                .toArray(Constructor[]::new);
    }

    /**
     * Finds a constructor by parameter types.
     *
     * @param <T>        the type of the class
     * @param clazz      the class to introspect (maybe {@code null})
     * @param paramTypes the parameter types (maybe {@code null})
     * @return the constructor, or {@code null} if not found
     * @since 1.0.0
     */
    public static <T> Constructor<T> findConstructor(Class<T> clazz, Class<?>... paramTypes) {
        return findConstructor(clazz, c -> Arrays.equals(c.getParameterTypes(), paramTypes));
    }

    /**
     * Finds the default (no-argument) constructor.
     *
     * @param <T>   the type of the class
     * @param clazz the class to introspect (maybe {@code null})
     * @return the default constructor, or {@code null} if not found
     * @since 1.0.0
     */
    public static <T> Constructor<T> findDefaultConstructor(Class<T> clazz) {
        return findConstructor(clazz, c -> c.getParameterCount() == 0);
    }

    /**
     * Finds a constructor by parameter count.
     *
     * @param <T>        the type of the class
     * @param clazz      the class to introspect (maybe {@code null})
     * @param paramCount the number of parameters
     * @return the constructor, or {@code null} if not found
     * @since 1.0.0
     */
    public static <T> Constructor<T> findConstructor(Class<T> clazz, int paramCount) {
        return findConstructor(clazz, c -> c.getParameterCount() == paramCount);
    }

    /**
     * Finds a single constructor matching the predicate.
     *
     * @param <T>       the type of the class
     * @param clazz     the class to introspect (maybe {@code null})
     * @param predicate the filter condition (must not be {@code null})
     * @return the constructor, or {@code null}
     * @since 1.0.0
     */
    public static <T> Constructor<T> findConstructor(Class<T> clazz, Predicate<Constructor<T>> predicate) {
        for (Constructor<T> constructor : getDeclaredConstructors(clazz)) {
            if (predicate.test(constructor)) return constructor;
        }
        return null;
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * FUNCTIONAL/CALLBACK
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Performs an action on all fields of the class hierarchy.
     *
     * @param clazz  the class to traverse (maybe {@code null})
     * @param action the action to perform (must not be {@code null})
     * @since 1.0.0
     */
    public static void doWithFields(Class<?> clazz, Consumer<Field> action) {
        doWithFields(clazz, action, null);
    }

    /**
     * Performs an action on all fields of the class hierarchy that match the filter.
     *
     * @param clazz  the class to traverse (maybe {@code null})
     * @param action the action to perform (must not be {@code null})
     * @param filter the filter condition (maybe {@code null})
     * @since 1.0.0
     */
    public static void doWithFields(Class<?> clazz, Consumer<Field> action, Predicate<Field> filter) {
        if (clazz == null || clazz == Object.class) return;

        Class<?> searchType = clazz;
        while (searchType != null && searchType != Object.class) {
            Field[] fields = getDeclaredFields(searchType);
            for (Field field : fields) {
                if (filter == null || filter.test(field)) {
                    try {
                        action.accept(field);
                    } catch (Throwable ex) {
                        handleReflectionException(new RuntimeException("Error executing action on field: " + field.getName(), ex));
                    }
                }
            }
            searchType = searchType.getSuperclass();
        }
    }

    /**
     * Performs an action on declared fields of the class that match the filter.
     *
     * @param clazz  the class to traverse (maybe {@code null})
     * @param action the action to perform (must not be {@code null})
     * @param filter the filter condition (maybe {@code null})
     * @since 1.0.0
     */
    public static void doWithDeclaredFields(Class<?> clazz, Consumer<Field> action, Predicate<Field> filter) {
        if (clazz == null) return;
        Field[] fields = getDeclaredFields(clazz);
        for (Field field : fields) {
            if (filter == null || filter.test(field)) {
                action.accept(field);
            }
        }
    }

    /**
     * Performs an action on all methods of the class hierarchy.
     *
     * @param clazz  the class to traverse (maybe {@code null})
     * @param action the action to perform (must not be {@code null})
     * @since 1.0.0
     */
    public static void doWithMethods(Class<?> clazz, Consumer<Method> action) {
        doWithMethods(clazz, action, null);
    }

    /**
     * Performs an action on all methods of the class hierarchy that match the filter.
     *
     * @param clazz  the class to traverse (maybe {@code null})
     * @param action the action to perform (must not be {@code null})
     * @param filter the filter condition (maybe {@code null})
     * @since 1.0.0
     */
    public static void doWithMethods(Class<?> clazz, Consumer<Method> action, Predicate<Method> filter) {
        if (clazz == null || clazz == Object.class) return;

        // Use smart search logic: traverse Class hierarchy and Interfaces
        Method[] methods = findMethods(clazz); // Leverage existing findMethods logic
        for (Method method : methods) {
            if (filter == null || filter.test(method)) {
                try {
                    action.accept(method);
                } catch (Throwable ex) {
                    handleReflectionException(new RuntimeException("Error executing action on method: " + method.getName(), ex));
                }
            }
        }
    }

    /**
     * Performs an action on declared methods of the class that match the filter.
     *
     * @param clazz  the class to traverse (maybe {@code null})
     * @param action the action to perform (must not be {@code null})
     * @param filter the filter condition (maybe {@code null})
     * @since 1.0.0
     */
    public static void doWithDeclaredMethods(Class<?> clazz, Consumer<Method> action, Predicate<Method> filter) {
        if (clazz == null) return;
        Method[] methods = getDeclaredMethods(clazz);
        for (Method method : methods) {
            if (filter == null || filter.test(method)) {
                action.accept(method);
            }
        }
    }

    /**
     * Performs an action on all constructors of the class.
     *
     * @param clazz  the class to traverse (maybe {@code null})
     * @param action the action to perform (must not be {@code null})
     * @since 1.0.0
     */
    public static void doWithConstructors(Class<?> clazz, Consumer<Constructor<?>> action) {
        if (clazz == null) return;
        Constructor<?>[] constructors = getDeclaredConstructors(clazz);
        for (Constructor<?> constructor : constructors) {
            try {
                action.accept(constructor);
            } catch (Throwable ex) {
                handleReflectionException(new RuntimeException("Error executing action on constructor", ex));
            }
        }
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * INVOCATION UTILS
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Invokes the specified method on the target object.
     *
     * @param method the method to invoke (must not be {@code null})
     * @param target the target object (maybe {@code null} for static methods)
     * @param args   the arguments to pass (maybe {@code null})
     * @return the result of the invocation
     * @since 1.0.0
     */
    public static Object invokeMethod(Method method, Object target, Object... args) {
        try {
            makeAccessible(method);
            return method.invoke(target, args);
        } catch (Exception ex) {
            handleReflectionException(ex);
        }
        return null;
    }

    /**
     * Invokes the specified static method.
     *
     * @param method the static method to invoke (must not be {@code null})
     * @param args   the arguments to pass (maybe {@code null})
     * @return the result of the invocation
     * @since 1.0.0
     */
    public static Object invokeStaticMethod(Method method, Object... args) {
        Assert.isTrue(Modifier.isStatic(method.getModifiers()), "Method must be static");
        return invokeMethod(method, null, args);
    }

    /**
     * Retrieves the value of the specified field from the target object.
     *
     * @param field  the field to retrieve (must not be {@code null})
     * @param target the target object (maybe {@code null} for static fields)
     * @return the value of the field
     * @since 1.0.0
     */
    public static Object getField(Field field, Object target) {
        try {
            makeAccessible(field);
            return field.get(target);
        } catch (IllegalAccessException ex) {
            handleReflectionException(ex);
        }
        return null;
    }

    /**
     * Returns the element at {@code index} from a raw {@link Collection} or Java array.
     * Uses {@link Array#get} for primitive and object arrays; converts {@link Collection} to list for index access.
     *
     * @param source the object to read from — must be a {@link Collection} or array, not null
     * @param index  the zero-based index, must be non-negative
     * @return the element at the given index
     * @throws IllegalArgumentException  if source is null, index is negative, or source is neither a collection nor an array
     * @throws IndexOutOfBoundsException if index is out of range
     * @since 1.0.0
     */
    public static Object getIndexedValue(Object source, int index) {
        if (source == null) throw new IllegalArgumentException("source must not be null");
        if (index < 0) throw new IllegalArgumentException("index must be non-negative");
        if (Collection.class.isAssignableFrom(source.getClass())) {
            return new ArrayList<>((Collection<?>) source).get(index);
        }
        if (source.getClass().isArray()) {
            return Array.get(source, index);
        }
        throw new IllegalArgumentException("source is not a collection or array: " + source.getClass());
    }

    /**
     * Sets the value of the specified field on the target object.
     *
     * @param field  the field to set (must not be {@code null})
     * @param target the target object (maybe {@code null} for static fields)
     * @param value  the new value (maybe {@code null})
     * @since 1.0.0
     */
    public static void setField(Field field, Object target, Object value) {
        try {
            makeAccessible(field);
            field.set(target, value);
        } catch (IllegalAccessException ex) {
            handleReflectionException(ex);
        }
    }

    /**
     * Returns a reusable getter function that reads the named field directly (bypassing getters).
     * The field is looked up once in the class hierarchy and made accessible; the returned
     * {@link Function} can be cached and applied repeatedly without further lookup overhead.
     *
     * <p>Use {@link BeanUtils#getProperty(Object, String)} instead when a getter method is preferred.</p>
     *
     * @param clazz     the class that declares (or inherits) the field (must not be {@code null})
     * @param fieldName the field name (must not be {@code null})
     * @return a {@code Function<Object, Object>} that reads the field from a given instance
     * @throws IllegalArgumentException if no field {@code fieldName} exists in the hierarchy
     * @since 1.0.0
     */
    public static Function<Object, Object> fieldGetter(Class<?> clazz, String fieldName) {
        Assert.notNull(clazz, "Class must not be null");
        Assert.notNull(fieldName, "Field name must not be null");
        Field field = findField(clazz, fieldName);
        if (field == null) throw new IllegalArgumentException(
                "No field '" + fieldName + "' found on " + clazz.getName() + " or its hierarchy");
        makeAccessible(field);
        return target -> getField(field, target);
    }

    /**
     * Returns a reusable setter function that writes the named field directly (bypassing setters).
     * The field is looked up once in the class hierarchy and made accessible; the returned
     * {@link BiConsumer} can be cached and applied repeatedly without further lookup overhead.
     *
     * <p>Use {@link BeanUtils#setProperty(Object, String, Object)} instead when a setter method is preferred.</p>
     *
     * @param clazz     the class that declares (or inherits) the field (must not be {@code null})
     * @param fieldName the field name (must not be {@code null})
     * @return a {@code BiConsumer<Object, Object>} that writes {@code (target, value)} to the field
     * @throws IllegalArgumentException if no field {@code fieldName} exists in the hierarchy
     * @since 1.0.0
     */
    public static BiConsumer<Object, Object> fieldSetter(Class<?> clazz, String fieldName) {
        Assert.notNull(clazz, "Class must not be null");
        Assert.notNull(fieldName, "Field name must not be null");
        Field field = findField(clazz, fieldName);
        if (field == null) throw new IllegalArgumentException(
                "No field '" + fieldName + "' found on " + clazz.getName() + " or its hierarchy");
        makeAccessible(field);
        return (target, value) -> setField(field, target, value);
    }

    /**
     * Instantiates the specified class using its default constructor.
     *
     * @param <T>   the type of the class
     * @param clazz the class to instantiate (must not be {@code null})
     * @return a new instance of the class
     * @since 1.0.0
     */
    public static <T> T instantiateClass(Class<T> clazz) {
        Assert.notNull(clazz, "Class must not be null");
        try {
            Constructor<T> ctor = findDefaultConstructor(clazz);
            if (ctor == null) {
                throw new NoSuchMethodException("No default constructor found for " + clazz.getName());
            }
            return invokeConstructor(ctor);
        } catch (Exception ex) {
            handleReflectionException(ex);
        }
        return null;
    }

    /**
     * Invokes the specified constructor.
     *
     * @param <T>  the type of the class
     * @param ctor the constructor to invoke (must not be {@code null})
     * @param args the arguments to pass (maybe {@code null})
     * @return a new instance of the class
     * @since 1.0.0
     */
    public static <T> T invokeConstructor(Constructor<T> ctor, Object... args) {
        try {
            if (!ctor.canAccess(null)) ctor.setAccessible(true);
            return ctor.newInstance(args);
        } catch (Exception ex) {
            handleReflectionException(ex);
        }
        return null;
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * ACCESSIBILITY
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Makes the given object accessible, suppressing Java language access checks.
     *
     * @param obj the object to make accessible (maybe {@code null})
     * @since 1.0.0
     */
    public static void makeAccessible(AccessibleObject obj) {
        if (obj == null) return;

        // canAccess(null) throws IllegalArgumentException for non-static instance members;
        // for those, unconditionally attempt setAccessible.
        if (obj instanceof Member m && !Modifier.isStatic(m.getModifiers())) {
            obj.setAccessible(true);
        } else if (!obj.canAccess(null)) {
            obj.setAccessible(true);
        }
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * EXCEPTION HANDLING
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Handles reflection exceptions by converting them to unchecked exceptions.
     *
     * @param ex the exception to handle
     * @throws IllegalStateException if the method is not found or not accessible
     * @throws RuntimeException      for other exceptions
     * @since 1.0.0
     */
    public static void handleReflectionException(Exception ex) {
        if (ex instanceof NoSuchMethodException) {
            throw new IllegalStateException("Method not found: " + ex.getMessage());
        }
        if (ex instanceof IllegalAccessException) {
            throw new IllegalStateException("Could not access method: " + ex.getMessage());
        }
        if (ex instanceof InvocationTargetException) {
            // Rethrow the original exception from inside the invoked method
            rethrowRuntimeException(((InvocationTargetException) ex).getTargetException());
        }
        throw new RuntimeException("Unexpected reflection exception", ex);
    }

    /**
     * Rethrows a throwable as a runtime exception.
     *
     * @param ex the throwable to rethrow
     * @throws RuntimeException the rethrown exception
     * @since 1.0.0
     */
    public static void rethrowRuntimeException(Throwable ex) {
        if (ex instanceof RuntimeException) throw (RuntimeException) ex;
        if (ex instanceof Error) throw (Error) ex;
        throw new RuntimeException("Undeclared checked exception", ex);
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * CACHE & HELPER METHODS
     * -----------------------------------------------------------------------------------------------------------------
     */

    private static void fillFields(Class<?> clazz, Map<String, Field> fieldMap) {
        for (Field f : getDeclaredFields(clazz)) {
            fieldMap.putIfAbsent(f.getName(), f);
        }
    }

    private static void fillMethods(Class<?> clazz, Map<MethodKey, Method> methodMap) {
        for (Method m : getDeclaredMethods(clazz)) {
            MethodKey methodKey = new MethodKey(m);
            methodMap.putIfAbsent(methodKey, m);
        }
    }

    private static Field[] getDeclaredFields(Class<?> clazz) {
        return fieldCache.computeIfAbsent(clazz, key -> {
            Field[] fields = key.getDeclaredFields();
            return fields.length > 0 ? fields : EMPTY_FIELD_ARRAY;
        });
    }

    private static Method[] getDeclaredMethods(Class<?> clazz) {
        return methodCache.computeIfAbsent(clazz, key -> {
            Method[] methods = key.getDeclaredMethods();
            return methods.length > 0 ? methods : EMPTY_METHOD_ARRAY;
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> Constructor<T>[] getDeclaredConstructors(Class<T> clazz) {
        return (Constructor<T>[]) constructorCache.computeIfAbsent(clazz, key -> {
            Constructor<?>[] ctors = key.getDeclaredConstructors();
            return ctors.length > 0 ? ctors : new Constructor<?>[0];
        });
    }
}
