package com.macro.mall.protection;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/** Reuses Gateway's atomic Lua token bucket, but propagates Redis failures instead of admitting traffic. */
@Component
public class StrictRedisRateLimiter {
    private final ReactiveStringRedisTemplate redis;
    private final RedisScript<List<Long>> script;
    private final GatewayProtectionSettings settings;

    public StrictRedisRateLimiter(ReactiveStringRedisTemplate redis,
                                  @Qualifier("redisRequestRateLimiterScript") RedisScript<List<Long>> script,
                                  GatewayProtectionSettings settings) {
        this.redis = redis;
        this.script = script;
        this.settings = settings;
    }

    public Mono<Void> acquire(String route, GatewayProtectionSettings.RateLimit policy) {
        String prefix = "mall:gateway:rate:{" + settings.namespace() + ":" + route + "}:";
        List<String> keys = List.of(prefix + "tokens", prefix + "timestamp");
        // Empty time argument uses Redis TIME, avoiding clock differences between gateway instances.
        List<String> args = List.of(Integer.toString(policy.replenishRate()),
                Integer.toString(policy.burstCapacity()), "", Integer.toString(policy.requestedTokens()));
        return Mono.defer(() -> redis.execute(script, keys, args).single())
                .timeout(settings.redisTimeout())
                .map(result -> {
                    if (result.size() != 2 || (result.get(0) != 0L && result.get(0) != 1L) || result.get(1) < 0) {
                        throw new IllegalStateException("Invalid Redis token bucket result for route " + route);
                    }
                    return result.get(0) == 1L;
                })
                .onErrorMap(error -> new GatewayProtectionException(GatewayProtectionError.REDIS_UNAVAILABLE, error))
                .flatMap(allowed -> allowed ? Mono.empty()
                        : Mono.error(new GatewayProtectionException(GatewayProtectionError.RATE_LIMITED, null)));
    }
}
