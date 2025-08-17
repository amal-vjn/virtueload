package com.aml.virtueload;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RateLimiter is a simple rate limiting utility that allows a specified number of permits per second.
 * It uses an atomic integer to track the available permits and refills them based on the elapsed time.
 */
public class RateLimiter {
    private final int permitsPerSecond;
    private final AtomicInteger permits;
    private long lastRefillTime;

    public RateLimiter(int permitsPerSecond) {
        this.permitsPerSecond = permitsPerSecond;
        this.permits = new AtomicInteger(permitsPerSecond);
        this.lastRefillTime = System.nanoTime();
    }

    public void acquire() throws InterruptedException {
        while (true) {
            refillIfNeeded();
            if (permits.decrementAndGet() >= 0) {
                return;
            }
            permits.incrementAndGet();
            TimeUnit.MILLISECONDS.sleep(50); // Wait before retrying
        }
    }

    private void refillIfNeeded() {
        long now = System.nanoTime();
        long elapsedNanos = now - lastRefillTime;
        long elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(elapsedNanos);
        
        if (elapsedSeconds > 0) {
            permits.set(permitsPerSecond);
            lastRefillTime = now;
        }
    }
}
