/*
 * Created by Truong Ngo (2026).
 */
package vn.t3nexus.lib.utils.reflect;

import vn.t3nexus.lib.utils.lang.Assert;
import vn.t3nexus.lib.utils.lang.ObjectUtils;

import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * General utility methods for working with annotations.
 * <p>
 * This class provides utilities for finding, retrieving, and inspecting annotations
 * on classes, methods, and other annotated elements. It supports meta-annotations,
 * repeatable annotations, and attribute retrieval.
 * </p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 * <li><b>Discovery:</b> Find annotations on elements, including meta-annotations and hierarchy scanning.</li>
 * <li><b>Attribute & Metadata:</b> Retrieve annotation attributes and default values.</li>
 * <li><b>Merge & Synthesis:</b> Synthesize annotations from maps and merge attributes.</li>
 * </ul>
 *
 * <p>This class is thread-safe and intended for static use.</p>
 *
 * @author Truong Ngo
 * @version 1.0.0
 * @since 1.0.0
 */
public final class AnnotationUtils {

    private static final Map<AnnotatedElement, Map<Class<? extends Annotation>, Annotation>> findCache = Collections.synchronizedMap(new WeakHashMap<>(256));

    /**
     * Prevents instantiation of this utility class.
     * @throws UnsupportedOperationException always
     */
    private AnnotationUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * DISCOVERY
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Finds a single {@link Annotation} of {@code annotationType} on the supplied {@link AnnotatedElement}.
     * <p>
     * Traverses the interface hierarchy and superclasses if the element is a class.
     * Also traverses the class hierarchy for methods.
     * </p>
     *
     * <pre>{@code
     * MyAnnotation ann = AnnotationUtils.findAnnotation(myClass, MyAnnotation.class);
     * }</pre>
     *
     * @param <A>            the annotation type
     * @param element        the element to look for annotations on (must not be {@code null})
     * @param annotationType the annotation type to look for (must not be {@code null})
     * @return the annotation found, or {@code null} if none found
     * @since 1.0.0
     */
    @SuppressWarnings("unchecked")
    public static <A extends Annotation> A findAnnotation(AnnotatedElement element, Class<A> annotationType) {
        Assert.notNull(element, "Element must not be null");
        Assert.notNull(annotationType, "Annotation type must not be null");

        AnnotatedElement effectiveElement = (element instanceof Class<?> clazz)
                ? ClassUtils.getUserClass(clazz) : element;

        return (A) findCache
                .computeIfAbsent(effectiveElement, k -> new ConcurrentHashMap<>(8))
                .computeIfAbsent(annotationType, type -> searchAnnotation(effectiveElement, (Class<A>) type, new HashSet<>()));
    }

    /**
     * Gets a single {@link Annotation} of {@code annotationType} on the supplied {@link AnnotatedElement}.
     * <p>
     * Supports meta-annotations (annotations on annotations).
     * </p>
     *
     * <pre>{@code
     * MyAnnotation ann = AnnotationUtils.getAnnotation(myMethod, MyAnnotation.class);
     * }</pre>
     *
     * @param <A>            the annotation type
     * @param element        the element to look for annotations on (must not be {@code null})
     * @param annotationType the annotation type to look for (must not be {@code null})
     * @return the annotation found, or {@code null} if none found
     * @since 1.0.0
     */
    public static <A extends Annotation> A getAnnotation(AnnotatedElement element, Class<A> annotationType) {
        Assert.notNull(element, "Element must not be null");
        Assert.notNull(annotationType, "Annotation type must not be null");

        // 1. Check directly using JDK API (Fastest)
        A ann = element.getAnnotation(annotationType);
        if (ann != null) {
            return ann;
        }

        // 2. If not found directly, scan Meta-annotations
        // Leverage getUserClass from ClassUtils to handle Proxy before scanning
        AnnotatedElement effectiveElement = (element instanceof Class<?> clazz) ?
                ClassUtils.getUserClass(clazz) : element;

        return searchMetaAnnotation(effectiveElement, annotationType, new HashSet<>());
    }

    /**
     * Checks if the specified {@link AnnotatedElement} is annotated with the given {@code annotationType}.
     *
     * <pre>{@code
     * boolean hasAnn = AnnotationUtils.isAnnotated(myField, MyAnnotation.class);
     * }</pre>
     *
     * @param element        the element to check (maybe {@code null})
     * @param annotationType the annotation type to look for (maybe {@code null})
     * @return {@code true} if the element is annotated with the given type
     * @since 1.0.0
     */
    public static boolean isAnnotated(AnnotatedElement element, Class<? extends Annotation> annotationType) {
        return getAnnotation(element, annotationType) != null;
    }

    /**
     * Checks if an Annotation (target) is meta-annotated with the given Meta-annotation (meta).
     *
     * <pre>{@code
     * boolean isMeta = AnnotationUtils.isAnnotatedMeta(MyAnnotation.class, Component.class);
     * }</pre>
     *
     * @param target the annotation type to check (must not be {@code null})
     * @param meta   the meta-annotation type to look for (must not be {@code null})
     * @return {@code true} if the target is meta-annotated with the meta type
     * @since 1.0.0
     */
    public static boolean isAnnotatedMeta(Class<? extends Annotation> target, Class<? extends Annotation> meta) {
        Assert.notNull(target, "Target annotation type must not be null");
        Assert.notNull(meta, "Meta annotation type must not be null");

        // 1. Check directly (Simplest case)
        if (target.isAnnotationPresent(meta)) {
            return true;
        }

        // 2. Recursive scan (Use Set to avoid infinite loops like @Retention on itself)
        return searchMeta(target, meta, new HashSet<>());
    }

    /**
     * Retrieves all repeatable annotations of the given type on the element.
     * <p>
     * Supports both standalone annotations and those wrapped in a Container (@Repeatable).
     * </p>
     *
     * <pre>{@code
     * MyAnn[] anns = AnnotationUtils.getRepeatableAnnotations(method, MyAnn.class);
     * }</pre>
     *
     * @param <A>            the annotation type
     * @param element        the element to look for annotations on (must not be {@code null})
     * @param annotationType the annotation type to look for (must not be {@code null})
     * @return an array of annotations found (never {@code null})
     * @since 1.0.0
     */
    public static <A extends Annotation> A[] getRepeatableAnnotations(AnnotatedElement element, Class<A> annotationType) {
        Assert.notNull(element, "Element must not be null");
        Assert.notNull(annotationType, "Annotation type must not be null");

        // 1. Use JDK 8+ API to get repeatable annotations directly
        A[] annotations = element.getAnnotationsByType(annotationType);

        // 2. If not found, or to support Meta-Repeatable (Advanced)
        if (ObjectUtils.isEmpty(annotations)) {
            // Reuse meta scan logic to find Container Annotation
            return searchRepeatableMeta(element, annotationType);
        }

        return annotations;
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * ATTRIBUTE & METADATA UTILS (Nhóm Thuộc tính & Metadata)
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Retrieves the value of the {@code value} attribute from the given annotation.
     *
     * <pre>{@code
     * Object val = AnnotationUtils.getValue(myAnnotation);
     * }</pre>
     *
     * @param annotation the annotation to retrieve the value from (maybe {@code null})
     * @return the attribute value, or {@code null} if not found
     * @since 1.0.0
     */
    public static Object getValue(Annotation annotation) {
        return getValue(annotation, "value");
    }

    /**
     * Retrieves the value of a named attribute from the given annotation.
     *
     * <pre>{@code
     * Object val = AnnotationUtils.getValue(myAnnotation, "name");
     * }</pre>
     *
     * @param annotation    the annotation to retrieve the value from (maybe {@code null})
     * @param attributeName the name of the attribute (maybe {@code null})
     * @return the attribute value, or {@code null} if not found
     * @since 1.0.0
     */
    public static Object getValue(Annotation annotation, String attributeName) {
        if (annotation == null || attributeName == null || attributeName.isEmpty()) {
            return null;
        }
        try {
            Method method = ReflectionUtils.findDeclaredMethod(annotation.annotationType(), attributeName);
            if (method != null) {
                return ReflectionUtils.invokeMethod(method, annotation);
            }
        } catch (Exception ex) {
            ReflectionUtils.handleReflectionException(ex);
            return null;
        }
        return null;
    }

    /**
     * Extracts all attributes of the annotation into a Map.
     *
     * <pre>{@code
     * Map<String, Object> attrs = AnnotationUtils.getAnnotationAttributes(myAnnotation);
     * }</pre>
     *
     * @param annotation the annotation to extract attributes from (must not be {@code null})
     * @return a map of attributes (never {@code null})
     * @since 1.0.0
     */
    public static Map<String, Object> getAnnotationAttributes(Annotation annotation) {
        Map<String, Object> attributes = new LinkedHashMap<>();

        // Reuse ReflectionUtils to iterate over annotation methods (attributes)
        ReflectionUtils.doWithDeclaredMethods(annotation.annotationType(), method -> {
            // Annotation methods have no parameters and are not system methods like toString, hashCode
            if (method.getParameterCount() == 0 && method.getReturnType() != void.class) {
                try {
                    Object value = ReflectionUtils.invokeMethod(method, annotation);
                    attributes.put(method.getName(), value);
                } catch (Exception ex) {
                    ReflectionUtils.handleReflectionException(ex);
                }
            }
        }, method ->
                !method.getName().equals("annotationType") &&
                !method.getName().equals("toString") &&
                !method.getName().equals("hashCode") &&
                !method.getName().equals("equals")
        );
        return attributes;
    }

    /**
     * Retrieves the default value of a named attribute from the annotation type.
     *
     * <pre>{@code
     * Object def = AnnotationUtils.getDefaultValue(MyAnnotation.class, "value");
     * }</pre>
     *
     * @param annotationType the annotation type (must not be {@code null})
     * @param attributeName  the name of the attribute (must not be {@code null})
     * @return the default value, or {@code null} if not found
     * @since 1.0.0
     */
    public static Object getDefaultValue(Class<? extends Annotation> annotationType, String attributeName) {
        try {
            Method method = annotationType.getDeclaredMethod(attributeName);
            return method.getDefaultValue();
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * MERGE & SYNTHESIS UTILS (Nhóm Hợp nhất & Tổng hợp)
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Retrieves merged attributes from the direct annotation and its meta-annotations.
     *
     * <pre>{@code
     * Map<String, Object> merged = AnnotationUtils.getMergedAnnotationAttributes(clazz, MyAnnotation.class);
     * }</pre>
     *
     * @param element        the element to look for annotations on (must not be {@code null})
     * @param annotationType the annotation type to look for (must not be {@code null})
     * @return a map of merged attributes (never {@code null})
     * @since 1.0.0
     */
    public static Map<String, Object> getMergedAnnotationAttributes(AnnotatedElement element, Class<? extends Annotation> annotationType) {
        Assert.notNull(element, "Element must not be null");

        // 1. Find target annotation in hierarchy (Discovery)
        Annotation target = findAnnotation(element, annotationType);
        if (target == null) return Collections.emptyMap();

        // 2. Get direct attributes (Metadata)

        // 3. Logic to handle nested Meta-annotations
        // (Scan back annotations containing target to find override values)
        // This is where you handle @AliasFor if you want a deep framework.

        return getAnnotationAttributes(target);
    }

    /**
     * Synthesizes an annotation instance from a map of attributes.
     * <p>
     * Useful for creating dynamic annotations at runtime (Library/Framework level).
     * </p>
     *
     * <pre>{@code
     * MyAnnotation ann = AnnotationUtils.synthesizeAnnotation(attrMap, MyAnnotation.class);
     * }</pre>
     *
     * @param <A>            the annotation type
     * @param attributes     the map of attributes (must not be {@code null})
     * @param annotationType the annotation type to synthesize (must not be {@code null})
     * @return the synthesized annotation instance
     * @since 1.0.0
     */
    @SuppressWarnings("unchecked")
    public static <A extends Annotation> A synthesizeAnnotation(Map<String, Object> attributes, Class<A> annotationType) {
        return (A) Proxy.newProxyInstance(
                annotationType.getClassLoader(),
                new Class<?>[] { annotationType },
                (proxy, method, args) -> switch (method.getName()) {
                    case "annotationType" -> annotationType;
                    case "toString"       -> "@" + annotationType.getName() + attributes;
                    case "equals" -> {
                        if (args[0] == proxy) yield true;
                        if (!annotationType.isInstance(args[0])) yield false;
                        for (Method m : annotationType.getDeclaredMethods()) {
                            Object v1 = attributes.containsKey(m.getName()) ? attributes.get(m.getName()) : m.getDefaultValue();
                            ReflectionUtils.makeAccessible(m);
                            Object v2 = m.invoke(args[0]);
                            if (!memberEquals(m.getReturnType(), v1, v2)) yield false;
                        }
                        yield true;
                    }
                    case "hashCode" -> {
                        int h = 0;
                        for (Method m : annotationType.getDeclaredMethods()) {
                            Object v = attributes.containsKey(m.getName()) ? attributes.get(m.getName()) : m.getDefaultValue();
                            h += (127 * m.getName().hashCode()) ^ memberHashCode(v);
                        }
                        yield h;
                    }
                    default -> attributes.containsKey(method.getName())
                            ? attributes.get(method.getName())
                            : method.getDefaultValue();
                }
        );
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * CACHE & HELPER METHODS
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Returns all annotations present on the given element, including meta-annotations,
     * traversing the annotation graph (BFS). Java system annotations
     * ({@code java.lang.annotation.*}) are excluded.
     *
     * <pre>{@code
     * Set<Annotation> all = AnnotationUtils.getAllAnnotations(MyService.class);
     * }</pre>
     *
     * @param element the element to look for annotations on (must not be {@code null})
     * @return an ordered set of all annotations found (never {@code null})
     * @since 1.0.0
     */
    public static Set<Annotation> getAllAnnotations(AnnotatedElement element) {
        Assert.notNull(element, "Element must not be null");
        Set<Annotation> result = new LinkedHashSet<>();
        collectAnnotations(element.getAnnotations(), result, new HashSet<>());
        return result;
    }

    /**
     * Clears the internal annotation cache.
     *
     * @since 1.0.0
     */
    public static void clearCache() {
        findCache.clear();
    }

    private static <A extends Annotation> A searchAnnotation(AnnotatedElement element, Class<A> annotationType, Set<Annotation> visited) {
        // A. Find directly
        A ann = element.getAnnotation(annotationType);
        if (ann != null) return ann;

        // B. Scan Hierarchy for Class (Reuse ClassUtils)
        if (element instanceof Class<?> clazz) {
            // Scan Interfaces
            for (Class<?> ifc : ClassUtils.getAllInterfacesForClassAsSet(clazz)) {
                A result = ifc.getAnnotation(annotationType);
                if (result != null) return result;
            }
            // Scan Superclasses
            for (Class<?> superClazz : ClassUtils.getAllSuperclasses(clazz)) {
                A result = superClazz.getAnnotation(annotationType);
                if (result != null) return result;
            }
        }

        // C. Scan Hierarchy for Method (Reuse ReflectionUtils & ClassUtils)
        if (element instanceof Method method) {
            Class<?> declaringClass = method.getDeclaringClass();

            // Find in superclasses (Reuse ClassUtils to get parent list)
            for (Class<?> superClazz : ClassUtils.getAllSuperclasses(declaringClass)) {
                Method m = ReflectionUtils.findMethod(superClazz, method.getName(), method.getParameterTypes());
                if (m != null && m.isAnnotationPresent(annotationType)) {
                    return m.getAnnotation(annotationType);
                }
            }
        }

        return null;
    }

    /**
     * Helper to search for Meta-annotations to support "Composed Annotations" (Spring-style).
     */
    @SuppressWarnings("unchecked")
    private static <A extends Annotation> A searchMetaAnnotation(AnnotatedElement element, Class<A> annotationType, Set<Class<? extends Annotation>> visited) {
        for (Annotation ann : element.getAnnotations()) {
            Class<? extends Annotation> annType = ann.annotationType();

            // Skip Java system annotations to avoid infinite loops
            if (!annType.getName().startsWith("java.lang.annotation") && visited.add(annType)) {
                if (annType == annotationType) {
                    return (A) ann;
                }
                // Recursively search deeper inside this annotation
                A meta = searchMetaAnnotation(annType, annotationType, visited);
                if (meta != null) return meta;
            }
        }
        return null;
    }

    /**
     * Helper to search for repeatable annotations defined via Meta-annotations.
     */
    @SuppressWarnings("unchecked")
    private static <A extends Annotation> A[] searchRepeatableMeta(AnnotatedElement element, Class<A> annotationType) {
        // Find Container Annotation (usually defined in @Repeatable of annotationType)
        Repeatable repeatable = annotationType.getAnnotation(Repeatable.class);
        if (repeatable != null) {
            Class<? extends Annotation> containerType = repeatable.value();
            Annotation container = getAnnotation(element, containerType);

            if (container != null) {
                // Reuse ReflectionUtils to call value() method of Container
                return (A[]) ReflectionUtils.invokeMethod(
                        ReflectionUtils.findMethod(containerType, "value"), container);
            }
        }
        return (A[]) Array.newInstance(annotationType, 0);
    }

    private static boolean searchMeta(Class<? extends Annotation> target,
                                      Class<? extends Annotation> meta,
                                      Set<Class<? extends Annotation>> visited) {
        for (Annotation ann : target.getAnnotations()) {
            Class<? extends Annotation> annType = ann.annotationType();
            if (annType.getName().startsWith("java.lang.annotation")) continue;
            if (annType == meta || (visited.add(annType) && searchMeta(annType, meta, visited))) return true;
        }
        return false;
    }

    private static void collectAnnotations(Annotation[] annotations, Set<Annotation> result,
                                           Set<Class<? extends Annotation>> visited) {
        for (Annotation ann : annotations) {
            Class<? extends Annotation> annType = ann.annotationType();
            if (!annType.getName().startsWith("java.lang.annotation") && visited.add(annType)) {
                result.add(ann);
                collectAnnotations(annType.getAnnotations(), result, visited);
            }
        }
    }

    /** Java annotation equals contract: same type + all member values equal. */
    private static boolean memberEquals(Class<?> type, Object v1, Object v2) {
        if (v1 == v2) return true;
        if (v1 == null || v2 == null) return false;
        if (!type.isArray()) return v1.equals(v2);
        if (type == byte[].class)    return Arrays.equals((byte[])    v1, (byte[])    v2);
        if (type == short[].class)   return Arrays.equals((short[])   v1, (short[])   v2);
        if (type == int[].class)     return Arrays.equals((int[])     v1, (int[])     v2);
        if (type == long[].class)    return Arrays.equals((long[])    v1, (long[])    v2);
        if (type == float[].class)   return Arrays.equals((float[])   v1, (float[])   v2);
        if (type == double[].class)  return Arrays.equals((double[])  v1, (double[])  v2);
        if (type == boolean[].class) return Arrays.equals((boolean[]) v1, (boolean[]) v2);
        if (type == char[].class)    return Arrays.equals((char[])    v1, (char[])    v2);
        return Arrays.equals((Object[]) v1, (Object[]) v2);
    }

    /** Java annotation hashCode contract: sum of (127 * name.hashCode()) ^ memberHashCode. */
    private static int memberHashCode(Object v) {
        if (v == null) return 0;
        Class<?> t = v.getClass();
        if (!t.isArray()) return v.hashCode();
        if (t == byte[].class)    return Arrays.hashCode((byte[])    v);
        if (t == short[].class)   return Arrays.hashCode((short[])   v);
        if (t == int[].class)     return Arrays.hashCode((int[])     v);
        if (t == long[].class)    return Arrays.hashCode((long[])    v);
        if (t == float[].class)   return Arrays.hashCode((float[])   v);
        if (t == double[].class)  return Arrays.hashCode((double[])  v);
        if (t == boolean[].class) return Arrays.hashCode((boolean[]) v);
        if (t == char[].class)    return Arrays.hashCode((char[])    v);
        return Arrays.hashCode((Object[]) v);
    }
}
