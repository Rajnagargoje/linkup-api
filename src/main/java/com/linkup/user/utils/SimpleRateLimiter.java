package com.linkup.user.utils;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fixed-window limiter: N requests per key per window, then blocked
 * until the window rolls over. In-memory only — fine for a single
 * instance; if LinkUp ever runs behind a load balancer with multiple
 * backend instances, this needs to move to Redis (INCR + EXPIRE) so all
 * instances share one counter instead of each allowing N requests.
 */
@Component
public class SimpleRateLimiter {

    private static class Window {
        final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());
        final AtomicLong count = new AtomicLong(0);
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public boolean tryConsume(String key, int maxRequests, long windowMillis) {
        Window window = windows.computeIfAbsent(key, k -> new Window());
        long now = System.currentTimeMillis();

        synchronized (window) {
            if (now - window.windowStart.get() > windowMillis) {
                window.windowStart.set(now);
                window.count.set(0);
            }
            if (window.count.get() >= maxRequests) {
                return false;
            }
            window.count.incrementAndGet();
            return true;
        }
    }
}
