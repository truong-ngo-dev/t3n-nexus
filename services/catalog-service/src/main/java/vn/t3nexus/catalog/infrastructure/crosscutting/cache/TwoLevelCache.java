package vn.t3nexus.catalog.infrastructure.crosscutting.cache;

import org.jetbrains.annotations.NotNull;
import org.springframework.cache.Cache;

import java.util.concurrent.Callable;

public class TwoLevelCache implements Cache {

    private final String name;
    private final Cache l1;
    private final Cache l2;

    public TwoLevelCache(String name, Cache l1, Cache l2) {
        this.name = name;
        this.l1   = l1;
        this.l2   = l2;
    }

    @NotNull
    @Override
    public String getName() {
        return name;
    }

    @NotNull
    @Override
    public Object getNativeCache() {
        return this;
    }

    @Override
    public ValueWrapper get(@NotNull Object key) {
        ValueWrapper hit = l1.get(key);
        if (hit != null) {
            return hit;
        }
        hit = l2.get(key);
        if (hit != null) {
            l1.put(key, hit.get());
        }
        return hit;
    }

    @Override
    public <T> T get(@NotNull Object key, Class<T> type) {
        T value = l1.get(key, type);
        if (value != null) {
            return value;
        }
        value = l2.get(key, type);
        if (value != null) {
            l1.put(key, value);
        }
        return value;
    }

    @Override
    public <T> T get(@NotNull Object key, @NotNull Callable<T> valueLoader) {
        ValueWrapper l1Hit = l1.get(key);
        if (l1Hit != null) {
            @SuppressWarnings("unchecked")
            T cached = (T) l1Hit.get();
            return cached;
        }
        T value = l2.get(key, valueLoader);
        if (value != null) {
            l1.put(key, value);
        }
        return value;
    }

    @Override
    public void put(@NotNull Object key, Object value) {
        l1.put(key, value);
        l2.put(key, value);
    }

    @Override
    public ValueWrapper putIfAbsent(@NotNull Object key, Object value) {
        ValueWrapper existing = get(key);
        if (existing != null) {
            return existing;
        }
        put(key, value);
        return null;
    }

    @Override
    public void evict(@NotNull Object key) {
        l1.evict(key);
        l2.evict(key);
    }

    @Override
    public void clear() {
        l1.clear();
        l2.clear();
    }
}
