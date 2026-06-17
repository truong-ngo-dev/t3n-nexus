package vn.t3nexus.catalog.infrastructure.crosscutting.cache;

import org.jetbrains.annotations.NotNull;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class TwoLevelCacheManager implements CacheManager {

    private final CacheManager l1Manager;
    private final CacheManager l2Manager;
    private final Set<String>  l1Names;

    public TwoLevelCacheManager(CacheManager l1Manager, CacheManager l2Manager, Set<String> l1Names) {
        this.l1Manager = l1Manager;
        this.l2Manager = l2Manager;
        this.l1Names   = Set.copyOf(l1Names);
    }

    @Override
    public Cache getCache(@NotNull String name) {
        Cache l2 = l2Manager.getCache(name);
        if (!l1Names.contains(name)) {
            return l2;
        }
        Cache l1 = l1Manager.getCache(name);
        if (l1 == null) {
            return l2;
        }
        return new TwoLevelCache(name, l1, l2);
    }

    @NotNull
    @Override
    public Collection<String> getCacheNames() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(l2Manager.getCacheNames()));
    }
}
