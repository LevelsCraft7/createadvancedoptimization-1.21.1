package net.levelsfr.createadvancedoptimization.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class BoundedLruMap<K, V> extends LinkedHashMap<K, V> {

    private final int maxEntries;
    private final Consumer<Map.Entry<K, V>> evictionListener;

    public BoundedLruMap(int maxEntries) {
        this(maxEntries, null);
    }

    public BoundedLruMap(int maxEntries, Consumer<Map.Entry<K, V>> evictionListener) {
        super(Math.max(4, maxEntries + 1), 0.75F, true);
        this.maxEntries = maxEntries;
        this.evictionListener = evictionListener;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        boolean remove = size() > maxEntries;
        if (remove && evictionListener != null) {
            evictionListener.accept(eldest);
        }
        return remove;
    }
}
