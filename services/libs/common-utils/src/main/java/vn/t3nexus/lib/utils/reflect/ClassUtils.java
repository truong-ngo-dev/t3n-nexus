/*
 * Created by Truong Ngo (2026).
 */
package vn.t3nexus.lib.utils.reflect;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Utility methods for working with {@link Class} instances.
 * <p>
 * This class provides comprehensive utilities for class loading, proxy handling,
 * primitive/wrapper conversion, and hierarchy analysis. It is designed to be
 * a robust replacement for standard reflection boilerplate.
 * </p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 * <li>Safe Class Loading (handling arrays, primitives, inner classes)</li>
 * <li>Proxy Resolution (JDK, CGLIB, Hibernate, ByteBuddy)</li>
 * <li>Type Assignability Checks (handling auto-boxing/widening)</li>
 * <li>Deep Hierarchy Analysis</li>
 * </ul>
 *
 * <p>This class is thread-safe and intended for static use.</p>
 *
 * @author Truong Ngo
 * @version 1.0.0
 * @see Class
 * @since 1.0.0
 */
public final class ClassUtils {

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * CACHE & CONSTANTS (Optimized for O(1) Lookup)
     * -----------------------------------------------------------------------------------------------------------------
     */

    /** Suffix for array class names: {@code "[]"}. */
    public static final String ARRAY_SUFFIX = "[]";

    /** Prefix for internal array class names: {@code "["}. */
    private static final String INTERNAL_ARRAY_PREFIX = "[";

    /** Prefix for internal non-primitive array class names: {@code "[L"}. */
    private static final String NON_PRIMITIVE_ARRAY_PREFIX = "[L";

    /** A reusable empty class array constant. */
    private static final Class<?>[] EMPTY_CLASS_ARRAY = {};

    /** The package separator character: {@code '.'}. */
    private static final char PACKAGE_SEPARATOR = '.';

    /** The path separator character: {@code '/'}. */
    private static final char PATH_SEPARATOR = '/';

    /** The nested class separator character: {@code '$'}. */
    private static final char NESTED_CLASS_SEPARATOR = '$';

    /** Marker for Lambda classes. */
    private static final String LAMBDA_CLASS_MARKER = "$$Lambda";

    /** The CGLIB class separator: {@code "$$"}. */
    public static final String CGLIB_CLASS_SEPARATOR = "$$";

    /**
     * The Hibernate proxy separator when using ByteBuddy.
     */
    public static final String HIBERNATE_PROXY_SEPARATOR = "$HibernateProxy$";

    /**
     * The raw ByteBuddy proxy separator.
     */
    public static final String BYTE_BUDDY_SEPARATOR = "$ByteBuddy$";

    /** The ".class" file suffix. */
    public static final String CLASS_FILE_SUFFIX = ".class";

    /** Map with primitive wrapper type as key and corresponding primitive type as value, for example: Integer.class -> int.class. */
    private static final Map<Class<?>, Class<?>> WRAPPER_TO_PRIMITIVE;

    /** Map with primitive type as key and corresponding wrapper type as value, for example: int.class -> Integer.class. */
    private static final Map<Class<?>, Class<?>> PRIMITIVE_TO_WRAPPER;

    /** Map with primitive name as key and corresponding primitive type as value, for example: "int" -> int.class. */
    private static final Map<String, Class<?>> PRIMITIVE_NAME_TO_TYPE;

    /** Map with primitive type as key and corresponding default value as value, for example: int.class -> 0. */
    private static final Map<Class<?>, Object> PRIMITIVE_DEFAULT_VALUES;

    /*
     * Initializes the static cache/mapping.
     * Loads configuration from primitive types safely.
     */
    static {
        Map<Class<?>, Class<?>> primToWrap = new HashMap<>(32);
        Map<Class<?>, Class<?>> wrapToPrim = new HashMap<>(32);
        Map<String, Class<?>> nameToPrim = new HashMap<>(32);

        addPrimitive(primToWrap, wrapToPrim, nameToPrim, boolean.class, Boolean.class);
        addPrimitive(primToWrap, wrapToPrim, nameToPrim, byte.class, Byte.class);
        addPrimitive(primToWrap, wrapToPrim, nameToPrim, char.class, Character.class);
        addPrimitive(primToWrap, wrapToPrim, nameToPrim, double.class, Double.class);
        addPrimitive(primToWrap, wrapToPrim, nameToPrim, float.class, Float.class);
        addPrimitive(primToWrap, wrapToPrim, nameToPrim, int.class, Integer.class);
        addPrimitive(primToWrap, wrapToPrim, nameToPrim, long.class, Long.class);
        addPrimitive(primToWrap, wrapToPrim, nameToPrim, short.class, Short.class);
        addPrimitive(primToWrap, wrapToPrim, nameToPrim, void.class, Void.class);

        PRIMITIVE_TO_WRAPPER = Collections.unmodifiableMap(primToWrap);
        WRAPPER_TO_PRIMITIVE = Collections.unmodifiableMap(wrapToPrim);
        PRIMITIVE_NAME_TO_TYPE = Collections.unmodifiableMap(nameToPrim);

        Map<Class<?>, Object> values = new HashMap<>(9);
        values.put(boolean.class, false);
        values.put(byte.class, (byte) 0);
        values.put(char.class, '\0');
        values.put(double.class, 0.0d);
        values.put(float.class, 0.0f);
        values.put(int.class, 0);
        values.put(long.class, 0L);
        values.put(short.class, (short) 0);
        values.put(void.class, null);

        PRIMITIVE_DEFAULT_VALUES = Collections.unmodifiableMap(values);
    }

    /**
     * Helper for static block.
     * Adds primitive type mappings to the provided maps.
     */
    private static void addPrimitive(Map<Class<?>, Class<?>> primToWrap,
                                     Map<Class<?>, Class<?>> wrapToPrim,
                                     Map<String, Class<?>> nameToPrim,
                                     Class<?> primitiveType,
                                     Class<?> wrapperType) {
        primToWrap.put(primitiveType, wrapperType);
        wrapToPrim.put(wrapperType, primitiveType);
        nameToPrim.put(primitiveType.getName(), primitiveType);
    }

    /**
     * Prevents instantiation of this utility class.
     * @throws UnsupportedOperationException always
     */
    private ClassUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * CLASS LOADING
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Returns the default ClassLoader.
     * <p>
     * Tries the Thread Context ClassLoader first, then falls back to the ClassLoader
     * of this {@code ClassUtils} class.
     * </p>
     *
     * <pre>{@code
     * ClassLoader cl = ClassUtils.getDefaultClassLoader();
     * }</pre>
     *
     * @return the default ClassLoader (never {@code null})
     * @since 1.0.0
     */
    public static ClassLoader getDefaultClassLoader() {
        ClassLoader cl = null;
        try {
            cl = Thread.currentThread().getContextClassLoader();
        } catch (Throwable ignored) {}
        if (cl == null) {
            cl = ClassUtils.class.getClassLoader();
        }
        return cl;
    }

    /**
     * Loads the class with the specified name using the given ClassLoader.
     * <p>
     * Supports primitive types (e.g., "int"), primitive arrays (e.g., "int[]"),
     * and internal array names (e.g., "[Ljava.lang.String;").
     * </p>
     *
     * <pre>{@code
     * Class<?> clazz = ClassUtils.forName("java.lang.String", loader);
     * Class<?> array = ClassUtils.forName("int[]", loader);
     * }</pre>
     *
     * @param name        the name of the class (must not be {@code null})
     * @param classLoader the class loader to use (maybe {@code null})
     * @return the loaded class (never {@code null})
     * @throws ClassNotFoundException   if the class could not be found
     * @throws IllegalArgumentException if the name is empty
     * @see Class#forName(String, boolean, ClassLoader)
     * @since 1.0.0
     */
    public static Class<?> forName(String name, ClassLoader classLoader) throws ClassNotFoundException {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Name must not be empty");
        }

        if (name.length() <= 7) {
            Class<?> clazz = PRIMITIVE_NAME_TO_TYPE.get(name);
            if (clazz != null) {
                return clazz;
            }
        }

        if (name.endsWith(ARRAY_SUFFIX)) {
            String elementClassName = name.substring(0, name.length() - ARRAY_SUFFIX.length());
            Class<?> elementClass = forName(elementClassName, classLoader);
            return Array.newInstance(elementClass, 0).getClass();
        }

        if (name.startsWith(NON_PRIMITIVE_ARRAY_PREFIX) && name.endsWith(";")) {
            String elementName = name.substring(NON_PRIMITIVE_ARRAY_PREFIX.length(), name.length() - 1);
            Class<?> elementClass = forName(elementName, classLoader);
            return Array.newInstance(elementClass, 0).getClass();
        }

        if (name.startsWith(INTERNAL_ARRAY_PREFIX)) {
            return Class.forName(name, false, classLoader);
        }

        try {
            return Class.forName(name, false, classLoader);
        } catch (ClassNotFoundException ex) {
            int lastDotIndex = name.lastIndexOf('.');
            if (lastDotIndex != -1) {
                String innerClassName = name.substring(0, lastDotIndex) + '$' + name.substring(lastDotIndex + 1);
                try {
                    return Class.forName(innerClassName, false, classLoader);
                } catch (ClassNotFoundException ex2) {
                    // Swallow ex2, throw original ex
                }
            }
            throw ex;
        }
    }

    /**
     * Loads the class with the specified name using the default ClassLoader.
     * <p>
     * This is a convenience method for {@link #forName(String, ClassLoader)}.
     * </p>
     *
     * <pre>{@code
     * Class<?> clazz = ClassUtils.forName("java.lang.String");
     * }</pre>
     *
     * @param name the name of the class (must not be {@code null})
     * @return the loaded class (never {@code null})
     * @throws ClassNotFoundException   if the class could not be found
     * @throws IllegalArgumentException if the name is empty
     * @see #forName(String, ClassLoader)
     * @since 1.0.0
     */
    public static Class<?> forName(String name) throws ClassNotFoundException {
        return forName(name, getDefaultClassLoader());
    }

    /**
     * Resolves the primitive class by name.
     * <p>
     * Returns the primitive class for names like "int", "long", etc.
     * </p>
     *
     * <pre>{@code
     * Class<?> intClass = ClassUtils.resolvePrimitiveClassName("int");
     * }</pre>
     *
     * @param name the name of the primitive class (maybe {@code null})
     * @return the primitive class, or {@code null} if not found
     * @since 1.0.0
     */
    public static Class<?> resolvePrimitiveClassName(String name) {
        Class<?> result = null;
        if (name != null && name.length() <= 7) {
            result = PRIMITIVE_NAME_TO_TYPE.get(name);
        }
        return result;  
    }

    /**
     * Checks if the specified class is present in the given ClassLoader.
     * <p>
     * This method attempts to load the class and returns {@code true} if successful,
     * suppressing any {@link ClassNotFoundException}.
     * </p>
     *
     * <pre>{@code
     * boolean exists = ClassUtils.isPresent("com.example.MyClass", loader);
     * }</pre>
     *
     * @param className   the name of the class to check (maybe {@code null})
     * @param classLoader the class loader to use (maybe {@code null})
     * @return {@code true} if the class is present
     * @since 1.0.0
     */
    public static boolean isPresent(String className, ClassLoader classLoader) {
        try {
            forName(className, classLoader);
            return true;
        } catch (IllegalAccessError err) {
            throw new IllegalStateException("Readability mismatch in inheritance hierarchy of class [" +  className + "]: " + err.getMessage(), err);
        }
        catch (Throwable ex) {
            // Typically ClassNotFoundException or NoClassDefFoundError...
            return false;
        }
    }

    /**
     * Checks if the specified class is present in the default ClassLoader.
     * <p>
     * This is a convenience method for {@link #isPresent(String, ClassLoader)}.
     * </p>
     *
     * <pre>{@code
     * boolean exists = ClassUtils.isPresent("com.example.MyClass");
     * }</pre>
     *
     * @param className the name of the class to check (maybe {@code null})
     * @return {@code true} if the class is present
     * @see #isPresent(String, ClassLoader)
     * @since 1.0.0
     */
    public static boolean isPresent(String className) {
        return isPresent(className, getDefaultClassLoader());
    }

    /**
     * Scans all classes in the given package and its sub-packages.
     * <p>
     * Handles both exploded directories ({@code file://}) and JAR files ({@code jar://})
     * via {@link JarURLConnection} — no framework dependency required.
     * Classes that fail to load are silently skipped.
     * </p>
     *
     * <pre>{@code
     * List<Class<?>> classes = ClassUtils.scanPackage("com.example.domain");
     * }</pre>
     *
     * @param packageName the base package to scan (must not be {@code null} or blank)
     * @return a list of loaded classes (never {@code null})
     * @throws IllegalArgumentException if {@code packageName} is null or blank
     * @throws RuntimeException         if classpath resource enumeration fails
     * @since 1.0.0
     */
    public static List<Class<?>> scanPackage(String packageName) {
        if (packageName == null || packageName.isBlank()) {
            throw new IllegalArgumentException("Package name must not be blank");
        }
        String packagePath = packageName.replace('.', '/');
        ClassLoader loader = getDefaultClassLoader();
        List<Class<?>> classes = new ArrayList<>();
        try {
            Enumeration<URL> resources = loader.getResources(packagePath);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                if ("file".equals(url.getProtocol())) {
                    scanDirectory(new File(url.toURI()), packageName, classes, loader);
                } else if ("jar".equals(url.getProtocol())) {
                    scanJar(url, packagePath, classes, loader);
                }
            }
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException("Failed to scan package '" + packageName + "'", e);
        }
        return classes;
    }

    private static void scanDirectory(File dir, String packageName, List<Class<?>> classes, ClassLoader loader) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, packageName + "." + file.getName(), classes, loader);
            } else if (file.getName().endsWith(".class")) {
                String className = packageName + "." + file.getName().replace(".class", "");
                try { classes.add(forName(className, loader)); }
                catch (ClassNotFoundException | LinkageError ignored) {}
            }
        }
    }

    private static void scanJar(URL url, String packagePath, List<Class<?>> classes, ClassLoader loader) throws IOException {
        JarURLConnection conn = (JarURLConnection) url.openConnection();
        try (JarFile jar = conn.getJarFile()) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!entry.isDirectory() && name.startsWith(packagePath + "/") && name.endsWith(".class")) {
                    String className = name.replace('/', '.').replace(".class", "");
                    try { classes.add(forName(className, loader)); }
                    catch (ClassNotFoundException | LinkageError ignored) {}
                }
            }
        }
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * PRIMITIVE & WRAPPER
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Checks if the specified class is a primitive or a primitive wrapper.
     *
     * <pre>{@code
     * boolean result = ClassUtils.isPrimitiveOrWrapper(Integer.class); // true
     * }</pre>
     *
     * @param clazz the class to check (maybe {@code null})
     * @return {@code true} if the class is a primitive or wrapper
     * @since 1.0.0
     */
    public static boolean isPrimitiveOrWrapper(Class<?> clazz) {
        if (clazz == null) return false;
        return clazz.isPrimitive() || isWrapperType(clazz);
    }

    /**
     * Checks if the specified class is a primitive wrapper type.
     *
     * <pre>{@code
     * boolean result = ClassUtils.isWrapperType(Integer.class); // true
     * }</pre>
     *
     * @param clazz the class to check (maybe {@code null})
     * @return {@code true} if the class is a wrapper type
     * @since 1.0.0
     */
    public static boolean isWrapperType(Class<?> clazz) {
        return WRAPPER_TO_PRIMITIVE.containsKey(clazz);
    }

    /**
     * Resolves the given class if it is a primitive wrapper class,
     * returning the corresponding primitive type instead.
     *
     * <pre>{@code
     * Class<?> prim = ClassUtils.resolvePrimitiveIfNecessary(Integer.class); // int.class
     * }</pre>
     *
     * @param clazz the class to resolve (maybe {@code null})
     * @return the corresponding primitive type, or the original class
     * @since 1.0.0
     */
    public static Class<?> resolvePrimitiveIfNecessary(Class<?> clazz) {
        return (clazz != null && !clazz.isPrimitive() && WRAPPER_TO_PRIMITIVE.containsKey(clazz))
                ? WRAPPER_TO_PRIMITIVE.get(clazz) : clazz;
    }

    /**
     * Resolves the given class if it is a primitive class,
     * returning the corresponding primitive wrapper type instead.
     *
     * <pre>{@code
     * Class<?> wrapper = ClassUtils.resolveWrapperIfNecessary(int.class); // Integer.class
     * }</pre>
     *
     * @param clazz the class to resolve (maybe {@code null})
     * @return the corresponding wrapper type, or the original class
     * @since 1.0.0
     */
    public static Class<?> resolveWrapperIfNecessary(Class<?> clazz) {
        return (clazz != null && clazz.isPrimitive()) ? PRIMITIVE_TO_WRAPPER.get(clazz) : clazz;
    }

    /**
     * Returns the default value for the specified primitive class.
     * <p>
     * Returns 0 for numbers, false for boolean, etc. Returns {@code null} for non-primitives.
     * </p>
     *
     * <pre>{@code
     * Object def = ClassUtils.getDefaultValue(int.class); // 0
     * }</pre>
     *
     * @param clazz the class to check (maybe {@code null})
     * @return the default value, or {@code null}
     * @since 1.0.0
     */
    public static Object getDefaultValue(Class<?> clazz) {
        return PRIMITIVE_DEFAULT_VALUES.get(clazz);
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * PROXY & IDENTITY
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Returns the user-defined class for the given object.
     * <p>
     * This method unwraps CGLIB, Hibernate, and ByteBuddy proxies to reveal the
     * underlying user class.
     * </p>
     *
     * <pre>{@code
     * Class<?> userClass = ClassUtils.getUserClass(myProxyObject);
     * }</pre>
     *
     * @param instance the object to check (must not be {@code null})
     * @return the user-defined class (never {@code null})
     * @throws IllegalArgumentException if the instance is null
     * @since 1.0.0
     */
    public static Class<?> getUserClass(Object instance) {
        if (instance == null) {
            throw new IllegalArgumentException("Instance must not be null");
        }
        return getUserClass(instance.getClass());
    }

    /**
     * Returns the user-defined class for the given class.
     * <p>
     * This method unwraps CGLIB, Hibernate, and ByteBuddy proxies to reveal the
     * underlying user class.
     * </p>
     *
     * <pre>{@code
     * Class<?> userClass = ClassUtils.getUserClass(myProxyClass);
     * }</pre>
     *
     * @param clazz the class to check (maybe {@code null})
     * @return the user-defined class, or {@code null} if input is null
     * @since 1.0.0
     */
    public static Class<?> getUserClass(Class<?> clazz) {
        if (clazz == null) {
            return null;
        }


        if (clazz.getName().contains(CGLIB_CLASS_SEPARATOR) ||
            clazz.getName().contains(HIBERNATE_PROXY_SEPARATOR) ||
            clazz.getName().contains(BYTE_BUDDY_SEPARATOR)) {

            Class<?> superclass = clazz.getSuperclass();
            if (superclass != null && !Object.class.equals(superclass)) {
                return superclass;
            }
        }

        // Logic 2: Handle JDK Dynamic Proxy
        // With Generic Lib, we CANNOT know where the original class is located in the InvocationHandler.
        // So we keep the Proxy class. Users will have to handle it themselves if they want to dig deeper.

        return clazz;
    }

    /**
     * Checks if the given object is any kind of Proxy.
     *
     * <pre>{@code
     * boolean isProxy = ClassUtils.isProxy(myObject);
     * }</pre>
     *
     * @param object the object to check (maybe {@code null})
     * @return {@code true} if the object is a proxy
     * @since 1.0.0
     */
    public static boolean isProxy(Object object) {
        return object != null && isProxyClass(object.getClass());
    }

    /**
     * Checks if the specified class is a proxy class.
     * <p>
     * Supports JDK Dynamic Proxies, CGLIB, Hibernate, and ByteBuddy proxies.
     * </p>
     *
     * <pre>{@code
     * boolean isProxy = ClassUtils.isProxyClass(myClass);
     * }</pre>
     *
     * @param clazz the class to check (maybe {@code null})
     * @return {@code true} if the class is a proxy
     * @since 1.0.0
     */
    public static boolean isProxyClass(Class<?> clazz) {
        if (clazz == null) {
            return false;
        }

        // 1. Check JDK Dynamic Proxy (Native Java Standard)
        // This is an important step for your library to be "Generic"
        if (java.lang.reflect.Proxy.isProxyClass(clazz)) {
            return true;
        }

        // 2. Check Framework-specific Proxies (Naming Convention)
        String name = clazz.getName();
        return name.contains(CGLIB_CLASS_SEPARATOR) ||
                name.contains(HIBERNATE_PROXY_SEPARATOR) ||
                name.contains(BYTE_BUDDY_SEPARATOR);
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * CLASS STRUCTURE & MODIFIERS
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Checks if the specified class is an inner class (non-static member class).
     * <p>
     * An inner class requires an instance of the enclosing class to be instantiated.
     * </p>
     *
     * <pre>{@code
     * boolean isInner = ClassUtils.isInnerClass(MyInner.class);
     * }</pre>
     *
     * @param clazz the class to check (maybe {@code null})
     * @return {@code true} if the class is an inner class
     * @since 1.0.0
     */
    public static boolean isInnerClass(Class<?> clazz) {
        return clazz != null && clazz.isMemberClass() && !Modifier.isStatic(clazz.getModifiers());
    }

    /**
     * Checks if the specified class is a static nested class.
     * <p>
     * A static nested class behaves like a top-level class and can be instantiated
     * independently without an enclosing instance.
     * </p>
     *
     * <pre>{@code
     * boolean isStaticNested = ClassUtils.isStaticNestedClass(MyStaticNested.class);
     * }</pre>
     *
     * @param clazz the class to check (maybe {@code null})
     * @return {@code true} if the class is a static nested class
     * @since 1.0.0
     */
    public static boolean isStaticNestedClass(Class<?> clazz) {
        return clazz != null && clazz.isMemberClass() && Modifier.isStatic(clazz.getModifiers());
    }

    /**
     * Checks if the specified class is anonymous.
     * <p>
     * Anonymous classes are often created for callbacks or event listeners
     * and usually should be ignored during scanning.
     * </p>
     *
     * <pre>{@code
     * boolean isAnon = ClassUtils.isAnonymousClass(myAnonClass);
     * }</pre>
     *
     * @param clazz the class to check (maybe {@code null})
     * @return {@code true} if the class is anonymous
     * @since 1.0.0
     */
    public static boolean isAnonymousClass(Class<?> clazz) {
        return clazz != null && clazz.isAnonymousClass();
    }

    /**
     * Checks if the specified class is synthetic (compiler-generated).
     * <p>
     * Synthetic classes/members do not exist in the source code (e.g., Lambda classes,
     * switch map classes, bridge methods).
     * </p>
     *
     * <pre>{@code
     * boolean isSynthetic = ClassUtils.isSynthetic(mySyntheticClass);
     * }</pre>
     *
     * @param clazz the class to check (maybe {@code null})
     * @return {@code true} if the class is synthetic
     * @since 1.0.0
     */
    public static boolean isSynthetic(Class<?> clazz) {
        return clazz != null && clazz.isSynthetic();
    }

    /**
     * Checks if the specified class is a Lambda Expression.
     * <p>
     * Lambda expressions are synthetic and hold behavior, not data state.
     * They should be ignored when scanning for data changes.
     * </p>
     *
     * <pre>{@code
     * boolean isLambda = ClassUtils.isLambda(myLambdaClass);
     * }</pre>
     *
     * @param clazz the class to check (maybe {@code null})
     * @return {@code true} if the class is a lambda expression
     * @since 1.0.0
     */
    public static boolean isLambda(Class<?> clazz) {
        return isSynthetic(clazz) && clazz.getName().contains(LAMBDA_CLASS_MARKER);
    }

    /**
     * Checks if the specified class is abstract (including interfaces).
     * <p>
     * Useful to prevent instantiation attempts.
     * </p>
     *
     * <pre>{@code
     * boolean isAbstract = ClassUtils.isAbstract(MyAbstract.class);
     * }</pre>
     *
     * @param clazz the class to check (maybe {@code null})
     * @return {@code true} if the class is abstract
     * @since 1.0.0
     */
    public static boolean isAbstract(Class<?> clazz) {
        return clazz != null && Modifier.isAbstract(clazz.getModifiers());
    }

    /**
     * Checks if the specified class is a concrete class (not abstract, not interface).
     *
     * <pre>{@code
     * boolean isConcrete = ClassUtils.isConcrete(MyClass.class);
     * }</pre>
     *
     * @param clazz the class to check (maybe {@code null})
     * @return {@code true} if the class is concrete
     * @since 1.0.0
     */
    public static boolean isConcrete(Class<?> clazz) {
        return clazz != null && !isAbstract(clazz) && !clazz.isInterface();
    }

    /**
     * Checks if the specified class is public.
     *
     * <pre>{@code
     * boolean isPublic = ClassUtils.isPublic(MyClass.class);
     * }</pre>
     *
     * @param clazz the class to check (maybe {@code null})
     * @return {@code true} if the class is public
     * @since 1.0.0
     */
    public static boolean isPublic(Class<?> clazz) {
        return clazz != null && Modifier.isPublic(clazz.getModifiers());
    }

    /**
     * Checks if the specified class is final.
     *
     * <pre>{@code
     * boolean isFinal = ClassUtils.isFinal(MyClass.class);
     * }</pre>
     *
     * @param clazz the class to check (maybe {@code null})
     * @return {@code true} if the class is final
     * @since 1.0.0
     */
    public static boolean isFinal(Class<?> clazz) {
        return clazz != null && Modifier.isFinal(clazz.getModifiers());
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * ASSIGNABILITY & HIERARCHY
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Checks if the right-hand side type maybe assigned to the left-hand side type.
     * <p>
     * Considers primitive wrapper classes as assignable to the corresponding primitive types.
     * Handles auto-boxing and widening (e.g., int to long).
     * </p>
     *
     * <pre>{@code
     * ClassUtils.isAssignable(int.class, Integer.class) == true
     * ClassUtils.isAssignable(long.class, int.class)    == true (Widening)
     * }</pre>
     *
     * @param lhsType the target type (left-hand side) (maybe {@code null})
     * @param rhsType the value type (right-hand side) (maybe {@code null})
     * @return {@code true} if {@code rhsType} is assignable to {@code lhsType}
     * @since 1.0.0
     */
    public static boolean isAssignable(Class<?> lhsType, Class<?> rhsType) {
        if (lhsType == null || rhsType == null) {
            return false;
        }

        // 1. Direct assignment (including inheritance)
        if (lhsType.isAssignableFrom(rhsType)) {
            return true;
        }

        // 2. Handle Auto-boxing & Widening
        if (lhsType.isPrimitive()) {
            // Need to unbox rhsType
            Class<?> resolvedPrimitive = WRAPPER_TO_PRIMITIVE.get(rhsType);
            if (resolvedPrimitive == null) {
                // rhsType might be a primitive itself that needs widening (e.g. int -> long)
                resolvedPrimitive = rhsType;
            }

            if (lhsType == resolvedPrimitive) {
                return true;
            }
            return isWideningPrimitive(lhsType, resolvedPrimitive);
        } else {
            // Need to box rhsType (primitive -> wrapper)
            Class<?> resolvedWrapper = PRIMITIVE_TO_WRAPPER.get(rhsType);
            return resolvedWrapper != null && lhsType.isAssignableFrom(resolvedWrapper);
        }
    }

    /**
     * Checks if the given value is assignable to the specified type.
     *
     * <pre>{@code
     * boolean valid = ClassUtils.isAssignableValue(Integer.class, 10);
     * }</pre>
     *
     * @param type  the target type (maybe {@code null})
     * @param value the value to check (maybe {@code null})
     * @return {@code true} if the value is assignable to the type
     * @since 1.0.0
     */
    public static boolean isAssignableValue(Class<?> type, Object value) {
        if (type == null) {
            return false;
        }
        // 1. Value is not null: Check value type against target type
        if (value != null) {
            return isAssignable(type, value.getClass());
        }
        // 2. Value is null: Only allowed if target is NOT primitive
        // (int a = null -> Error; Integer a = null -> OK)
        return !type.isPrimitive();
    }

    /**
     * Returns a list of all superclasses for the given class.
     *
     * <pre>{@code
     * List<Class<?>> supers = ClassUtils.getAllSuperclasses(ArrayList.class);
     * }</pre>
     *
     * @param clazz the class to check (maybe {@code null})
     * @return the list of superclasses (never {@code null})
     * @since 1.0.0
     */
    public static List<Class<?>> getAllSuperclasses(Class<?> clazz) {
        if (clazz == null) {
            return Collections.emptyList();
        }
        List<Class<?>> classes = new ArrayList<>();
        Class<?> superclass = clazz.getSuperclass();
        while (superclass != null && superclass != Object.class) {
            classes.add(superclass);
            superclass = superclass.getSuperclass();
        }
        return classes;
    }

    /**
     * Returns a Set of all interfaces implemented by the given class and its superclasses.
     * <p>
     * The order is maintained using {@link LinkedHashSet}. Useful for Proxy generation
     * or finding marker interfaces.
     * </p>
     *
     * <pre>{@code
     * Set<Class<?>> ifcSet = ClassUtils.getAllInterfacesForClassAsSet(MyClass.class);
     * }</pre>
     *
     * @param clazz the class to look up (must not be {@code null})
     * @return the Set of interfaces (never {@code null})
     * @since 1.0.0
     */
    public static Set<Class<?>> getAllInterfacesForClassAsSet(Class<?> clazz) {
        if (clazz.isInterface()) {
            return Collections.singleton(clazz);
        }
        Set<Class<?>> interfaces = new LinkedHashSet<>();
        Class<?> current = clazz;
        while (current != null) {
            Class<?>[] ifc = current.getInterfaces();
            for (Class<?> i : ifc) {
                interfaces.add(i);
                interfaces.addAll(getAllInterfacesForClassAsSet(i));
            }
            current = current.getSuperclass();
        }
        return interfaces;
    }

    /**
     * Helper for {@link #isAssignable(Class, Class)}.
     * Checks if a primitive type can be widened to another primitive type.
     * e.g. int -> long, float -> double.
     */
    private static boolean isWideningPrimitive(Class<?> lhs, Class<?> rhs) {
        if (lhs == double.class) {
            return rhs == float.class || rhs == long.class || rhs == int.class || rhs == char.class || rhs == short.class || rhs == byte.class;
        }
        if (lhs == float.class) {
            return rhs == long.class || rhs == int.class || rhs == char.class || rhs == short.class || rhs == byte.class;
        }
        if (lhs == long.class) {
            return rhs == int.class || rhs == char.class || rhs == short.class || rhs == byte.class;
        }
        if (lhs == int.class) {
            return rhs == char.class || rhs == short.class || rhs == byte.class;
        }
        if (lhs == short.class) {
            // char cannot be widened to short: char is unsigned 16-bit, short is signed 16-bit
            return rhs == byte.class;
        }
        return false;
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * NAMING & PRESENTATION
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Returns the short name of the class.
     * <p>
     * Strips the package name and handles inner classes.
     * </p>
     *
     * <pre>{@code
     * String name = ClassUtils.getShortName(MyClass.class);
     * }</pre>
     *
     * @param clazz the class to check (maybe {@code null})
     * @return the short name (never {@code null})
     * @since 1.0.0
     */
    public static String getShortName(Class<?> clazz) {
        return getShortName(clazz != null ? clazz.getName() : null);
    }

    /**
     * Returns the short name of the class from its qualified name.
     *
     * <pre>{@code
     * String name = ClassUtils.getShortName("com.example.MyClass");
     * }</pre>
     *
     * @param className the qualified class name (maybe {@code null})
     * @return the short name (never {@code null})
     * @since 1.0.0
     */
    public static String getShortName(String className) {
        if (className == null || className.isEmpty()) {
            return "";
        }

        int lastDotIdx = className.lastIndexOf(PACKAGE_SEPARATOR);
        int startIdx = lastDotIdx + 1;
        int nameEndIdx = className.length();

        // Helper function to find the earliest marker safely (only searches in the class name part)
        nameEndIdx = findEarliestMarker(className, CGLIB_CLASS_SEPARATOR, startIdx, nameEndIdx);
        nameEndIdx = findEarliestMarker(className, HIBERNATE_PROXY_SEPARATOR, startIdx, nameEndIdx);
        nameEndIdx = findEarliestMarker(className, BYTE_BUDDY_SEPARATOR, startIdx, nameEndIdx);

        String shortName = className.substring(startIdx, nameEndIdx);
        shortName = shortName.replace(NESTED_CLASS_SEPARATOR, PACKAGE_SEPARATOR);
        return shortName;
    }

    /**
     * Helper for {@link #getShortName(String)}.
     * Finds the earliest occurrence of a proxy marker in the class name.
     */
    private static int findEarliestMarker(String className, String marker, int fromIndex, int currentEndIdx) {
        int idx = className.indexOf(marker, fromIndex); // Only search after the package separator
        if (idx != -1) {
            return Math.min(currentEndIdx, idx);
        }
        return currentEndIdx;
    }

    /**
     * Returns the package name of the class.
     *
     * <pre>{@code
     * String pkg = ClassUtils.getPackageName(MyClass.class);
     * }</pre>
     *
     * @param clazz the class to check (maybe {@code null})
     * @return the package name (never {@code null})
     * @since 1.0.0
     */
    public static String getPackageName(Class<?> clazz) {
        if (clazz == null) return "";
        if (clazz.isArray()) {
            return getPackageName(clazz.getComponentType());
        }
        if (clazz.isPrimitive()) {
            return "java.lang";
        }
        String className = clazz.getName();
        int lastDotIdx = className.lastIndexOf(PACKAGE_SEPARATOR);
        return (lastDotIdx != -1 ? className.substring(0, lastDotIdx) : "");
    }

    /**
     * Returns the qualified name of the class.
     *
     * <pre>{@code
     * String name = ClassUtils.getQualifiedName(MyClass.class);
     * }</pre>
     *
     * @param clazz the class to check (maybe {@code null})
     * @return the qualified name (never {@code null})
     * @since 1.0.0
     */
    public static String getQualifiedName(Class<?> clazz) {
        if (clazz == null) return "";
        if (clazz.isArray()) {
            return getQualifiedName(clazz.getComponentType()) + ARRAY_SUFFIX;
        }
        return clazz.getName();
    }

    /**
     * Returns a descriptive type name for the given value.
     * <p>
     * Handles proxies by including implemented interfaces.
     * </p>
     *
     * <pre>{@code
     * String desc = ClassUtils.getDescriptiveType(myObject);
     * }</pre>
     *
     * @param value the value to check (maybe {@code null})
     * @return the descriptive type name (never {@code null})
     * @since 1.0.0
     */
    public static String getDescriptiveType(Object value) {
        if (value == null) {
            return "null";
        }
        Class<?> clazz = value.getClass();
        if (isProxy(value)) {
            StringBuilder sb = new StringBuilder(clazz.getName());
            sb.append(" implementing ");
            Class<?>[] interfaces = clazz.getInterfaces();
            for (int i = 0; i < interfaces.length; i++) {
                sb.append(interfaces[i].getSimpleName());
                if (i < interfaces.length - 1) {
                    sb.append(',');
                }
            }
            return sb.toString();
        }
        return getQualifiedName(clazz);
    }
}
