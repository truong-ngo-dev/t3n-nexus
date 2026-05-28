package vn.t3nexus.lib.utils.reflect;

import vn.t3nexus.lib.utils.lang.Assert;
import vn.t3nexus.lib.utils.type.ConverterUtils;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Utility for Java Beans manipulation.
 * Kết hợp tốc độ của Spring (Cache PD) và sự linh hoạt của Apache (Populate, Nested).
 * * @author Truong Ngo
 */
public final class BeanUtils {

    private static final Map<Class<?>, PropertyDescriptor[]> PD_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>(256));

    private BeanUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * INSTANTIATION
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Instantiates {@code clazz} via its declared no-argument constructor.
     * <p>
     * Enforces the JavaBean contract: a bean must have a public no-arg constructor.
     * For general-purpose instantiation (including constructors with arguments) use
     * {@link ReflectionUtils#instantiateClass(Class)}.
     * </p>
     *
     * @param clazz the class to instantiate (must not be {@code null}, must not be an interface)
     * @param <T>   the type of the class
     * @return a new instance of {@code clazz}
     * @throws IllegalArgumentException if {@code clazz} is an interface
     * @throws RuntimeException         if instantiation fails
     */
    public static <T> T instantiateClass(Class<T> clazz) {
        Assert.notNull(clazz, "Class must not be null");
        if (clazz.isInterface()) {
            throw new IllegalArgumentException("Cannot instantiate an interface: " + clazz.getName());
        }
        try {
            Constructor<T> ctor = clazz.getDeclaredConstructor();
            ReflectionUtils.makeAccessible(ctor);
            return ctor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate [" + clazz.getName() + "]", e);
        }
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * METADATA & CACHING
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Returns all {@link PropertyDescriptor}s for {@code clazz}, including the inherited {@code class} property.
     * Results are cached per class to avoid repeated introspection.
     *
     * @param clazz the class to introspect (must not be {@code null})
     * @return array of property descriptors (never {@code null})
     */
    public static PropertyDescriptor[] getPropertyDescriptors(Class<?> clazz) {
        Assert.notNull(clazz, "Class must not be null");
        return PD_CACHE.computeIfAbsent(clazz, k -> {
            try {
                BeanInfo beanInfo = Introspector.getBeanInfo(k);
                return beanInfo.getPropertyDescriptors();
            } catch (Exception e) {
                throw new RuntimeException("Could not get PropertyDescriptors for " + k.getName(), e);
            }
        });
    }

    /**
     * Returns the {@link PropertyDescriptor} for {@code propertyName} on {@code clazz},
     * or {@code null} if not found.
     *
     * @param clazz        the class to introspect (must not be {@code null})
     * @param propertyName the property name (must not be {@code null})
     * @return the descriptor, or {@code null}
     */
    public static PropertyDescriptor getPropertyDescriptor(Class<?> clazz, String propertyName) {
        for (PropertyDescriptor pd : getPropertyDescriptors(clazz)) {
            if (pd.getName().equals(propertyName)) return pd;
        }
        return null;
    }

    /**
     * Returns {@code true} if {@code clazz} exposes a property named {@code propertyName}.
     *
     * @param clazz        the class to check (must not be {@code null})
     * @param propertyName the property name to look up
     * @return {@code true} if the property exists
     */
    public static boolean hasProperty(Class<?> clazz, String propertyName) {
        Assert.notNull(clazz, "Class must not be null");
        return getPropertyDescriptor(clazz, propertyName) != null;
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * PROPERTY ACCESS
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Gets the value of {@code propertyName} from {@code bean} via its getter method.
     *
     * @param bean         the bean instance (must not be {@code null})
     * @param propertyName the property name (must not be {@code null})
     * @return the property value, or {@code null} if the property has no getter
     * @throws IllegalArgumentException if no property {@code propertyName} exists on the bean
     * @throws RuntimeException         if the getter invocation fails
     */
    public static Object getProperty(Object bean, String propertyName) {
        Assert.notNull(bean, "Bean must not be null");
        Assert.notNull(propertyName, "Property name must not be null");
        PropertyDescriptor pd = getPropertyDescriptor(bean.getClass(), propertyName);
        if (pd == null) throw new IllegalArgumentException(
                "No property '" + propertyName + "' found on " + bean.getClass().getName());
        Method readMethod = pd.getReadMethod();
        if (readMethod == null) return null;
        try {
            ReflectionUtils.makeAccessible(readMethod);
            return readMethod.invoke(bean);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to get property '" + propertyName + "' from " + bean.getClass().getName(), e);
        }
    }

    /**
     * Sets {@code value} on {@code bean}'s {@code propertyName} via its setter method.
     * Performs automatic type conversion via {@link ConverterUtils} if the value type
     * does not match the target property type.
     *
     * @param bean         the bean instance (must not be {@code null})
     * @param propertyName the property name (must not be {@code null})
     * @param value        the value to set (maybe {@code null})
     * @throws IllegalArgumentException if no property {@code propertyName} exists on the bean
     */
    public static void setProperty(Object bean, String propertyName, Object value) {
        Assert.notNull(bean, "Bean must not be null");
        Assert.notNull(propertyName, "Property name must not be null");
        PropertyDescriptor pd = getPropertyDescriptor(bean.getClass(), propertyName);
        if (pd == null) throw new IllegalArgumentException(
                "No property '" + propertyName + "' found on " + bean.getClass().getName());
        setPropertyValue(bean, pd, value);
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * PROPERTY MANIPULATION
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Copies matching properties from {@code source} to {@code target}.
     * Properties listed in {@code ignoreProperties} are skipped.
     * Per-property failures (type mismatch, missing setter) are silently skipped —
     * this mirrors the behavior of Spring's {@code BeanUtils.copyProperties}.
     *
     * @param source           the source bean (must not be {@code null})
     * @param target           the target bean (must not be {@code null})
     * @param ignoreProperties property names to exclude from copying
     */
    public static void copyProperties(Object source, Object target, String... ignoreProperties) {
        copyProperties(source, target, false, ignoreProperties);
    }

    /**
     * Copies matching properties from {@code source} to {@code target}.
     * Per-property failures are silently skipped.
     *
     * @param source           the source bean (must not be {@code null})
     * @param target           the target bean (must not be {@code null})
     * @param ignoreNull       if {@code true}, properties with a {@code null} value in source are not copied
     * @param ignoreProperties property names to exclude from copying
     */
    public static void copyProperties(Object source, Object target, boolean ignoreNull, String... ignoreProperties) {
        Assert.notNull(source, "Source must not be null");
        Assert.notNull(target, "Target must not be null");

        Set<String> ignoreSet = ignoreProperties != null
                ? new HashSet<>(Arrays.asList(ignoreProperties))
                : Collections.emptySet();

        PropertyDescriptor[] targetPds = getPropertyDescriptors(target.getClass());
        PropertyDescriptor[] sourcePds = getPropertyDescriptors(source.getClass());

        for (PropertyDescriptor targetPd : targetPds) {
            if (targetPd.getWriteMethod() == null || ignoreSet.contains(targetPd.getName())) continue;

            PropertyDescriptor sourcePd = findPropertyDescriptor(sourcePds, targetPd.getName());
            if (sourcePd == null || sourcePd.getReadMethod() == null) continue;

            try {
                Object value = sourcePd.getReadMethod().invoke(source);
                if (value == null && ignoreNull) continue;
                setPropertyValue(target, targetPd, value);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Populates {@code target} bean from a {@code Map}, matching map keys to property names.
     * Unknown keys are silently skipped.
     *
     * @param target the bean to populate (maybe {@code null} — no-op)
     * @param map    the source map (maybe {@code null} — no-op)
     */
    public static void populate(Object target, Map<String, ?> map) {
        if (target == null || map == null) return;
        for (Map.Entry<String, ?> entry : map.entrySet()) {
            PropertyDescriptor pd = getPropertyDescriptor(target.getClass(), entry.getKey());
            if (pd != null) setPropertyValue(target, pd, entry.getValue());
        }
    }

    /**
     * Converts {@code bean} into a {@code Map<String, Object>} keyed by property name.
     * The synthetic {@code class} property is excluded.
     * Properties whose getter throws are silently skipped.
     *
     * @param bean the bean to describe (maybe {@code null} — returns empty map)
     * @return a map of property names to their values
     */
    public static Map<String, Object> describe(Object bean) {
        if (bean == null) return Collections.emptyMap();
        Map<String, Object> map = new LinkedHashMap<>();
        for (PropertyDescriptor pd : getPropertyDescriptors(bean.getClass())) {
            if (pd.getReadMethod() == null || "class".equals(pd.getName())) continue;
            try {
                map.put(pd.getName(), pd.getReadMethod().invoke(bean));
            } catch (Exception ignored) {}
        }
        return map;
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * NESTED PROPERTIES
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Gets the value of a nested property using dot-notation (e.g. {@code "order.customer.name"}).
     * Returns {@code null} if any intermediate property is {@code null} or missing.
     *
     * @param bean       the root bean (maybe {@code null} — returns {@code null})
     * @param expression the dot-notation property path (maybe {@code null} — returns {@code null})
     * @return the resolved value, or {@code null}
     */
    public static Object getNestedProperty(Object bean, String expression) {
        if (bean == null || expression == null) return null;
        Object current = bean;
        for (String segment : expression.split("\\.")) {
            if (current == null) return null;
            PropertyDescriptor pd = getPropertyDescriptor(current.getClass(), segment);
            if (pd == null || pd.getReadMethod() == null) return null;
            try {
                current = pd.getReadMethod().invoke(current);
            } catch (Exception e) {
                return null;
            }
        }
        return current;
    }

    /**
     * Sets the value of a nested property using dot-notation (e.g. {@code "order.customer.name"}).
     * Traverses the path up to the second-to-last segment, then sets the final property.
     *
     * @param bean       the root bean (must not be {@code null})
     * @param expression the dot-notation property path (must not be {@code null})
     * @param value      the value to set
     * @throws IllegalArgumentException if any intermediate segment resolves to {@code null}
     *                                  or the final property does not exist
     */
    public static void setNestedProperty(Object bean, String expression, Object value) {
        Assert.notNull(bean, "Bean must not be null");
        Assert.notNull(expression, "Expression must not be null");
        int lastDot = expression.lastIndexOf('.');
        if (lastDot == -1) {
            setProperty(bean, expression, value);
            return;
        }
        Object parent = getNestedProperty(bean, expression.substring(0, lastDot));
        if (parent == null) throw new IllegalArgumentException(
                "Cannot traverse path '" + expression.substring(0, lastDot) + "' — null encountered");
        setProperty(parent, expression.substring(lastDot + 1), value);
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * DIFF
     * -----------------------------------------------------------------------------------------------------------------
     */

    /**
     * Holds the old and new value of a single changed property.
     * Intended for use as the value type in the map returned by {@link #diff}.
     *
     * @param oldValue the value before the change (maybe {@code null})
     * @param newValue the value after the change  (maybe {@code null})
     */
    public record PropertyChange(Object oldValue, Object newValue) {}

    /**
     * Compares {@code source} and {@code target} property by property and returns an ordered map
     * of changed properties. Each entry maps a property name to a {@link PropertyChange} holding
     * the before and after values. Useful for constructing audit-log entries.
     *
     * <p>Only readable properties (those with a getter on both beans) are compared.
     * The synthetic {@code class} property is always excluded.
     * Properties whose getter throws are silently skipped.</p>
     *
     * @param source           the original bean (must not be {@code null})
     * @param target           the modified bean (must not be {@code null})
     * @param ignoreProperties property names to exclude from comparison
     * @return an ordered map of changed property names to their before/after values (never {@code null})
     */
    public static Map<String, PropertyChange> diff(Object source, Object target, String... ignoreProperties) {
        Assert.notNull(source, "Source must not be null");
        Assert.notNull(target, "Target must not be null");

        Set<String> ignoreSet = new HashSet<>();
        ignoreSet.add("class");
        if (ignoreProperties != null) ignoreSet.addAll(Arrays.asList(ignoreProperties));

        Map<String, PropertyChange> changes = new LinkedHashMap<>();
        for (PropertyDescriptor pd : getPropertyDescriptors(source.getClass())) {
            String name = pd.getName();
            if (ignoreSet.contains(name) || pd.getReadMethod() == null) continue;
            PropertyDescriptor targetPd = getPropertyDescriptor(target.getClass(), name);
            if (targetPd == null || targetPd.getReadMethod() == null) continue;
            try {
                Object oldVal = pd.getReadMethod().invoke(source);
                Object newVal = targetPd.getReadMethod().invoke(target);
                if (!Objects.equals(oldVal, newVal)) {
                    changes.put(name, new PropertyChange(oldVal, newVal));
                }
            } catch (Exception ignored) {}
        }
        return changes;
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * HELPERS
     * -----------------------------------------------------------------------------------------------------------------
     */

    private static void setPropertyValue(Object target, PropertyDescriptor pd, Object value) {
        Method writeMethod = pd.getWriteMethod();
        if (writeMethod == null) return;
        try {
            Class<?> targetType = pd.getPropertyType();
            Object converted = (value != null && !targetType.isAssignableFrom(value.getClass()))
                    ? ConverterUtils.convert(value, targetType)
                    : value;
            ReflectionUtils.makeAccessible(writeMethod);
            writeMethod.invoke(target, converted);
        } catch (Exception ignored) {}
    }

    private static PropertyDescriptor findPropertyDescriptor(PropertyDescriptor[] pds, String name) {
        for (PropertyDescriptor pd : pds) {
            if (pd.getName().equals(name)) return pd;
        }
        return null;
    }
}