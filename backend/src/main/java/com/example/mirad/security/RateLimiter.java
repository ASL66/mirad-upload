package com.example.mirad.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class RateLimiter {
    private final ConcurrentHashMap<String, WindowState> windows = new ConcurrentHashMap<>();
    private final AtomicInteger requestCounter = new AtomicInteger();

    public boolean allow(String bucket, String subject, int limit, Duration window) {
        long now = System.currentTimeMillis();
        long expiresAt = now + window.toMillis();
        String key = bucket + ":" + subject;
        AtomicReference<Boolean> allowed = new AtomicReference<>(Boolean.FALSE);

        windows.compute(key, (ignored, current) -> {
            if (current == null || now >= current.expiresAt) {
                allowed.set(Boolean.TRUE);
                return new WindowState(1, expiresAt);
            }
            if (current.count < limit) {
                allowed.set(Boolean.TRUE);
                return new WindowState(current.count + 1, current.expiresAt);
            }
            return current;
        });

        if (requestCounter.incrementAndGet() % 256 == 0) {
            cleanup(now);
        }

        return allowed.get();
    }

    private void cleanup(long now) {
        for (Map.Entry<String, WindowState> entry : windows.entrySet()) {
            if (entry.getValue().expiresAt < now) {
                windows.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private record WindowState(int count, long expiresAt) {
    }
}
