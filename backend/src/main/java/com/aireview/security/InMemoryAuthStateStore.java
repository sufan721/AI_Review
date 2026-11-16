package com.aireview.security;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/** 内存实现：单机测试/轻量部署用，过期项在读取时惰性清理。 */
public class InMemoryAuthStateStore implements AuthStateStore {
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    private record Entry(String value, long expireAtMillis) {
        boolean expired(long now) {
            return now >= expireAtMillis;
        }
    }

    @Override
    public void put(String key, String value, Duration ttl) {
        entries.put(key, new Entry(value, expireAt(ttl)));
    }

    @Override
    public String getAndDelete(String key) {
        Entry removed = entries.remove(key);
        System.err.println("[DEBUG getAndDelete] store=" + System.identityHashCode(this)
            + " key=" + key + " removedNull=" + (removed == null) + " size=" + entries.size());
        if (removed == null || removed.expired(System.currentTimeMillis())) {
            return null;
        }
        return removed.value();
    }

    @Override
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        long now = System.currentTimeMillis();
        Entry fresh = new Entry(value, expireAt(ttl));
        Entry winner = entries.putIfAbsent(key, fresh);
        System.err.println("[DEBUG setIfAbsent] store=" + System.identityHashCode(this)
            + " key=" + key + " winnerNull=" + (winner == null) + " size=" + entries.size());
        if (winner == null) {
            return true;
        }
        // 已有项已过期时视同不存在：尝试用新值顶替，替换成功才算自己写入
        return winner.expired(now) && entries.replace(key, winner, fresh);
    }

    @Override
    public boolean exists(String key) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return false;
        }
        if (entry.expired(System.currentTimeMillis())) {
            entries.remove(key, entry);
            return false;
        }
        return true;
    }

    @Override
    public void delete(String key) {
        entries.remove(key);
    }

    private static long expireAt(Duration ttl) {
        return System.currentTimeMillis() + ttl.toMillis();
    }
}
