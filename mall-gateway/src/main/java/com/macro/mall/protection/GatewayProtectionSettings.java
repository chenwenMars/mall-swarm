package com.macro.mall.protection;

import org.springframework.util.Assert;

import java.time.Duration;
import java.util.Map;

/** Validated startup snapshot. Deliberately not a refreshable ConfigurationProperties bean. */
public record GatewayProtectionSettings(String namespace, Duration redisTimeout, Map<String, RoutePolicy> routes) {
    public GatewayProtectionSettings {
        Assert.isTrue(namespace != null && namespace.matches("[a-zA-Z0-9:_-]+"),
                "mall.gateway.protection.namespace must identify application and environment (letters, digits, :, _, -)");
        positive(redisTimeout, "redis-timeout");
        Assert.notEmpty(routes, "mall.gateway.protection.routes must not be empty");
        routes.forEach((id, policy) -> {
            Assert.isTrue(id.matches("[a-zA-Z0-9_-]+"), "Invalid protection route id: " + id);
            Assert.notNull(policy, "Missing protection policy for " + id);
        });
        routes = Map.copyOf(routes);
    }

    public record RoutePolicy(RateLimit rateLimit, CircuitBreaker circuitBreaker, Boolean degradationEnabled) {
        public RoutePolicy {
            Assert.notNull(rateLimit, "rate-limit is required");
            Assert.notNull(circuitBreaker, "circuit-breaker is required");
            Assert.notNull(degradationEnabled, "degradation-enabled is required");
        }
    }

    public record RateLimit(Boolean enabled, int replenishRate, int burstCapacity, int requestedTokens) {
        public RateLimit {
            Assert.notNull(enabled, "rate-limit.enabled is required");
            Assert.isTrue(replenishRate > 0, "replenish-rate must be positive");
            Assert.isTrue(burstCapacity >= replenishRate, "burst-capacity must be >= replenish-rate");
            Assert.isTrue(requestedTokens > 0 && requestedTokens <= burstCapacity,
                    "requested-tokens must be between 1 and burst-capacity");
        }
    }

    public record CircuitBreaker(Boolean enabled, int slidingWindowSize, int minimumNumberOfCalls,
                                 float failureRateThreshold, Duration waitDurationInOpenState,
                                 int permittedNumberOfCallsInHalfOpenState, Duration timeout) {
        public CircuitBreaker {
            Assert.notNull(enabled, "circuit-breaker.enabled is required");
            Assert.isTrue(slidingWindowSize > 0, "sliding-window-size must be positive");
            Assert.isTrue(minimumNumberOfCalls > 0 && minimumNumberOfCalls <= slidingWindowSize,
                    "minimum-number-of-calls must be between 1 and sliding-window-size");
            Assert.isTrue(failureRateThreshold > 0 && failureRateThreshold <= 100,
                    "failure-rate-threshold must be in (0, 100]");
            positive(waitDurationInOpenState, "wait-duration-in-open-state");
            Assert.isTrue(permittedNumberOfCallsInHalfOpenState > 0,
                    "permitted-number-of-calls-in-half-open-state must be positive");
            positive(timeout, "timeout");
        }
    }

    private static void positive(Duration duration, String name) {
        Assert.isTrue(duration != null && duration.toMillis() > 0, name + " must be at least 1ms");
    }
}
