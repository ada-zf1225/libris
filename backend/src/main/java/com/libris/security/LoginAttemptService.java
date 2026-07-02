package com.libris.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * In-memory sliding-window limiter for failed sign-in attempts, keyed by
 * username + client IP. Sufficient for a single-node deployment; swap for a
 * shared store when scaling out.
 */
@Service
public class LoginAttemptService {

    private final int maxAttempts;
    private final Duration window;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public LoginAttemptService(
            @Value("${libris.security.login.max-attempts:5}") int maxAttempts,
            @Value("${libris.security.login.lockout-minutes:15}") int lockoutMinutes) {
        this.maxAttempts = maxAttempts;
        this.window = Duration.ofMinutes(lockoutMinutes);
    }

    public boolean isBlocked(String username, String ip) {
        Deque<Instant> deque = failures.get(key(username, ip));
        if (deque == null) {
            return false;
        }
        prune(deque);
        return deque.size() >= maxAttempts;
    }

    public void recordFailure(String username, String ip) {
        Deque<Instant> deque = failures.computeIfAbsent(key(username, ip), k -> new ConcurrentLinkedDeque<>());
        deque.addLast(Instant.now());
        prune(deque);
    }

    public void reset(String username, String ip) {
        failures.remove(key(username, ip));
    }

    private void prune(Deque<Instant> deque) {
        Instant cutoff = Instant.now().minus(window);
        while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
            deque.pollFirst();
        }
    }

    private String key(String username, String ip) {
        return username + "|" + ip;
    }
}
