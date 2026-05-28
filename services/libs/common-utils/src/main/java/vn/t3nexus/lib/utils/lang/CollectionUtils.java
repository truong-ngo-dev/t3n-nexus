package vn.t3nexus.lib.utils.lang;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * General-purpose Collection and Map utilities — null-safe by default.
 * <p>
 * Methods accept null inputs and return empty collections/maps rather than throwing,
 * unless the contract explicitly states otherwise (e.g. {@link #single(Collection)}).
 * Map operations preserve insertion order via {@link LinkedHashMap}.
 * </p>
 *
 * @author Truong Ngo
 * @version 1.0.0
 * @since 1.0.0
 */
public final class CollectionUtils {

    private CollectionUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * NULL / EMPTY CHECKS
     * -----------------------------------------------------------------------------------------------------------------
     */

    /** Returns {@code true} if {@code c} is null or empty. */
    public static boolean isEmpty(Collection<?> c) {
        return c == null || c.isEmpty();
    }

    /** Returns {@code true} if {@code c} is non-null and non-empty. */
    public static boolean isNotEmpty(Collection<?> c) {
        return !isEmpty(c);
    }

    /** Returns {@code true} if {@code m} is null or empty. */
    public static boolean isEmpty(Map<?, ?> m) {
        return m == null || m.isEmpty();
    }

    /** Returns {@code true} if {@code m} is non-null and non-empty. */
    public static boolean isNotEmpty(Map<?, ?> m) {
        return !isEmpty(m);
    }

    /** Returns the size of {@code c}, or {@code 0} if null. */
    public static int size(Collection<?> c) {
        return c == null ? 0 : c.size();
    }

    /** Returns the size of {@code m}, or {@code 0} if null. */
    public static int size(Map<?, ?> m) {
        return m == null ? 0 : m.size();
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * DEFAULTS
     * -----------------------------------------------------------------------------------------------------------------
     */

    /** Returns {@code list} if non-null, otherwise {@link Collections#emptyList()}. */
    public static <T> List<T> nullToEmpty(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }

    /** Returns {@code set} if non-null, otherwise {@link Collections#emptySet()}. */
    public static <T> Set<T> nullToEmpty(Set<T> set) {
        return set == null ? Collections.emptySet() : set;
    }

    /** Returns {@code map} if non-null, otherwise {@link Collections#emptyMap()}. */
    public static <K, V> Map<K, V> nullToEmpty(Map<K, V> map) {
        return map == null ? Collections.emptyMap() : map;
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * ACCESS
     * -----------------------------------------------------------------------------------------------------------------
     */

    /** Returns the first element of {@code c}, or {@code null} if empty. */
    public static <T> T firstOrNull(Collection<T> c) {
        if (isEmpty(c)) return null;
        return c instanceof List<T> list ? list.getFirst() : c.iterator().next();
    }

    /** Returns the last element of {@code list}, or {@code null} if empty. */
    public static <T> T lastOrNull(List<T> list) {
        return isEmpty(list) ? null : list.getLast();
    }

    /** Returns the element at {@code index}, or {@code null} if out of bounds. */
    public static <T> T getOrNull(List<T> list, int index) {
        if (isEmpty(list) || index < 0 || index >= list.size()) return null;
        return list.get(index);
    }

    /**
     * Returns the single element of {@code c}.
     *
     * @throws NoSuchElementException  if {@code c} is empty
     * @throws IllegalStateException   if {@code c} has more than one element
     */
    public static <T> T single(Collection<T> c) {
        if (isEmpty(c)) throw new NoSuchElementException("Collection is empty");
        if (c.size() > 1) throw new IllegalStateException("Expected single element but got " + c.size());
        return c instanceof List<T> list ? list.getFirst() : c.iterator().next();
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * TRANSFORMATION
     * -----------------------------------------------------------------------------------------------------------------
     */

    /** Copies {@code c} into a new {@link ArrayList}. */
    public static <T> List<T> toList(Collection<T> c) {
        return isEmpty(c) ? Collections.emptyList() : new ArrayList<>(c);
    }

    /** Copies {@code c} into a new {@link LinkedHashSet}, preserving order and removing duplicates. */
    public static <T> Set<T> toSet(Collection<T> c) {
        return isEmpty(c) ? Collections.emptySet() : new LinkedHashSet<>(c);
    }

    /** Collects {@code c} into a {@code Map} keyed by {@code keyMapper}, using identity as value. */
    public static <T, K> Map<K, T> toMap(Collection<T> c, Function<T, K> keyMapper) {
        return toMap(c, keyMapper, Function.identity());
    }

    /** Collects {@code c} into a {@code Map} keyed by {@code keyMapper} and valued by {@code valueMapper}. */
    public static <T, K, V> Map<K, V> toMap(Collection<T> c, Function<T, K> keyMapper, Function<T, V> valueMapper) {
        if (isEmpty(c)) return Collections.emptyMap();
        return c.stream().collect(Collectors.toMap(keyMapper, valueMapper));
    }

    /** Groups elements of {@code c} by {@code classifier} into a {@code Map<K, List<T>>}. */
    public static <T, K> Map<K, List<T>> groupBy(Collection<T> c, Function<T, K> classifier) {
        if (isEmpty(c)) return Collections.emptyMap();
        return c.stream().collect(Collectors.groupingBy(classifier));
    }

    /**
     * Partitions {@code c} by {@code predicate}.
     * Returns {@code Map<Boolean, List<T>>} where {@code true} → matched, {@code false} → unmatched.
     */
    public static <T> Map<Boolean, List<T>> partition(Collection<T> c, Predicate<T> predicate) {
        if (isEmpty(c)) return Map.of(Boolean.TRUE, Collections.emptyList(), Boolean.FALSE, Collections.emptyList());
        return c.stream().collect(Collectors.partitioningBy(predicate));
    }

    /**
     * Splits {@code list} into consecutive sublists of at most {@code size} elements.
     * The last chunk may be smaller if the list size is not evenly divisible.
     *
     * <pre>{@code chunk([1,2,3,4,5], 2) == [[1,2], [3,4], [5]]}</pre>
     *
     * @throws IllegalArgumentException if {@code size} is not positive
     */
    public static <T> List<List<T>> chunk(List<T> list, int size) {
        if (isEmpty(list)) return Collections.emptyList();
        if (size <= 0) throw new IllegalArgumentException("Chunk size must be positive, got: " + size);
        List<List<T>> chunks = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            chunks.add(new ArrayList<>(list.subList(i, Math.min(i + size, list.size()))));
        }
        return chunks;
    }

    /** Flat-maps each element of {@code c} via {@code mapper}, skipping null results. */
    public static <T, R> List<R> flatMap(Collection<T> c, Function<T, Collection<R>> mapper) {
        if (isEmpty(c)) return Collections.emptyList();
        return c.stream()
                .map(mapper)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    /** Returns a deduplicated list preserving encounter order. */
    public static <T> List<T> distinct(Collection<T> c) {
        if (isEmpty(c)) return Collections.emptyList();
        return c.stream().distinct().collect(Collectors.toList());
    }

    /** Returns a list of elements matching {@code predicate}. */
    public static <T> List<T> filter(Collection<T> c, Predicate<T> predicate) {
        if (isEmpty(c)) return Collections.emptyList();
        return c.stream().filter(predicate).collect(Collectors.toList());
    }

    /** Transforms each element of {@code c} via {@code mapper} into a new list. */
    public static <T, R> List<R> transform(Collection<T> c, Function<T, R> mapper) {
        if (isEmpty(c)) return Collections.emptyList();
        return c.stream().map(mapper).collect(Collectors.toList());
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * SET OPERATIONS
     * -----------------------------------------------------------------------------------------------------------------
     */

    /** Returns all unique elements from both collections, preserving insertion order. */
    public static <T> Set<T> union(Collection<T> a, Collection<T> b) {
        Set<T> result = new LinkedHashSet<>();
        if (isNotEmpty(a)) result.addAll(a);
        if (isNotEmpty(b)) result.addAll(b);
        return result;
    }

    /** Returns elements present in both collections, preserving order of {@code a}. */
    public static <T> Set<T> intersection(Collection<T> a, Collection<T> b) {
        if (isEmpty(a) || isEmpty(b)) return Collections.emptySet();
        Set<T> setB = new HashSet<>(b);
        return a.stream().filter(setB::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Returns elements in {@code a} that are not present in {@code b}. */
    public static <T> List<T> difference(Collection<T> a, Collection<T> b) {
        if (isEmpty(a)) return Collections.emptyList();
        if (isEmpty(b)) return toList(a);
        Set<T> setB = new HashSet<>(b);
        return a.stream().filter(e -> !setB.contains(e)).collect(Collectors.toList());
    }

    /** Returns elements present in either {@code a} or {@code b}, but not in both (symmetric difference). */
    public static <T> Set<T> disjunction(Collection<T> a, Collection<T> b) {
        Set<T> result = union(a, b);
        result.removeAll(intersection(a, b));
        return result;
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * SEARCH
     * -----------------------------------------------------------------------------------------------------------------
     */

    /** Returns the first element matching {@code predicate}, or {@link Optional#empty()}. */
    public static <T> Optional<T> findFirst(Collection<T> c, Predicate<T> predicate) {
        if (isEmpty(c)) return Optional.empty();
        return c.stream().filter(predicate).findFirst();
    }

    /** Returns {@code true} if any element matches {@code predicate}. */
    public static <T> boolean anyMatch(Collection<T> c, Predicate<T> predicate) {
        return isNotEmpty(c) && c.stream().anyMatch(predicate);
    }

    /** Returns {@code true} if all elements match {@code predicate} (vacuously true for empty). */
    public static <T> boolean allMatch(Collection<T> c, Predicate<T> predicate) {
        if (isEmpty(c)) return true;
        return c.stream().allMatch(predicate);
    }

    /** Returns {@code true} if no element matches {@code predicate} (vacuously true for empty). */
    public static <T> boolean noneMatch(Collection<T> c, Predicate<T> predicate) {
        if (isEmpty(c)) return true;
        return c.stream().noneMatch(predicate);
    }

    /** Returns the count of elements matching {@code predicate}. */
    public static <T> long countMatches(Collection<T> c, Predicate<T> predicate) {
        if (isEmpty(c)) return 0;
        return c.stream().filter(predicate).count();
    }

    /*
     * -----------------------------------------------------------------------------------------------------------------
     * MAP OPERATIONS
     * -----------------------------------------------------------------------------------------------------------------
     */

    /** Returns {@code map.get(key)}, or {@code defaultValue} if the map is null or the key is absent. */
    public static <K, V> V getOrDefault(Map<K, V> map, K key, V defaultValue) {
        return isEmpty(map) ? defaultValue : map.getOrDefault(key, defaultValue);
    }

    /**
     * Returns a new map with keys and values swapped.
     * Duplicate values become duplicate keys — last-write wins.
     */
    public static <K, V> Map<V, K> invertMap(Map<K, V> map) {
        if (isEmpty(map)) return Collections.emptyMap();
        return map.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey,
                        (a, b) -> b, LinkedHashMap::new));
    }

    /**
     * Merges multiple maps into one; later maps override earlier maps on key conflict.
     */
    @SafeVarargs
    public static <K, V> Map<K, V> merge(Map<K, V>... maps) {
        Map<K, V> result = new LinkedHashMap<>();
        for (Map<K, V> map : maps) {
            if (isNotEmpty(map)) result.putAll(map);
        }
        return result;
    }

    /**
     * Merges {@code override} into {@code base}; conflicts are resolved by {@code mergeFunction}.
     */
    public static <K, V> Map<K, V> merge(Map<K, V> base, Map<K, V> override, BinaryOperator<V> mergeFunction) {
        if (isEmpty(base)) return isEmpty(override) ? Collections.emptyMap() : new LinkedHashMap<>(override);
        Map<K, V> result = new LinkedHashMap<>(base);
        if (isNotEmpty(override)) override.forEach((k, v) -> result.merge(k, v, mergeFunction));
        return result;
    }

    /** Returns a new map containing only entries whose key matches {@code predicate}. */
    public static <K, V> Map<K, V> filterByKey(Map<K, V> map, Predicate<K> predicate) {
        if (isEmpty(map)) return Collections.emptyMap();
        return map.entrySet().stream()
                .filter(e -> predicate.test(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }

    /** Returns a new map containing only entries whose value matches {@code predicate}. */
    public static <K, V> Map<K, V> filterByValue(Map<K, V> map, Predicate<V> predicate) {
        if (isEmpty(map)) return Collections.emptyMap();
        return map.entrySet().stream()
                .filter(e -> predicate.test(e.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }

    /** Returns a new map containing only entries matching {@code predicate}. */
    public static <K, V> Map<K, V> filterEntries(Map<K, V> map, Predicate<Map.Entry<K, V>> predicate) {
        if (isEmpty(map)) return Collections.emptyMap();
        return map.entrySet().stream()
                .filter(predicate)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }

    /**
     * Returns a new map with keys transformed by {@code keyMapper}.
     * On key collision after transformation, last-write wins.
     */
    public static <K, K2, V> Map<K2, V> mapKeys(Map<K, V> map, Function<K, K2> keyMapper) {
        if (isEmpty(map)) return Collections.emptyMap();
        return map.entrySet().stream()
                .collect(Collectors.toMap(e -> keyMapper.apply(e.getKey()), Map.Entry::getValue,
                        (a, b) -> b, LinkedHashMap::new));
    }

    /** Returns a new map with values transformed by {@code valueMapper}. */
    public static <K, V, V2> Map<K, V2> mapValues(Map<K, V> map, Function<V, V2> valueMapper) {
        if (isEmpty(map)) return Collections.emptyMap();
        return map.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> valueMapper.apply(e.getValue()),
                        (a, b) -> a, LinkedHashMap::new));
    }

    /** Returns a new map with both keys and values transformed. On key collision, last-write wins. */
    public static <K, K2, V, V2> Map<K2, V2> mapEntries(Map<K, V> map,
                                                          Function<K, K2> keyMapper,
                                                          Function<V, V2> valueMapper) {
        if (isEmpty(map)) return Collections.emptyMap();
        return map.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> keyMapper.apply(e.getKey()),
                        e -> valueMapper.apply(e.getValue()),
                        (a, b) -> b,
                        LinkedHashMap::new));
    }
}
