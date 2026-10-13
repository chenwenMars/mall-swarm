package com.macro.mall.protection;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SpringCloudCircuitBreakerFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SpringCloudCircuitBreakerResilience4JFilterFactory;
import org.springframework.cloud.gateway.support.HasRouteId;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;

import java.util.Set;

@Component
public class GatewayProtectionGatewayFilterFactory
        extends AbstractGatewayFilterFactory<GatewayProtectionGatewayFilterFactory.Config> {
    public static final String CONTEXT_ATTRIBUTE = GatewayProtectionGatewayFilterFactory.class.getName() + ".context";
    private final GatewayProtectionSettings settings;
    private final StrictRedisRateLimiter limiter;
    private final SpringCloudCircuitBreakerResilience4JFilterFactory circuitBreakers;
    private final MeterRegistry meters;

    public GatewayProtectionGatewayFilterFactory(GatewayProtectionSettings settings, StrictRedisRateLimiter limiter,
            SpringCloudCircuitBreakerResilience4JFilterFactory circuitBreakers, MeterRegistry meters) {
        super(Config.class);
        this.settings = settings;
        this.limiter = limiter;
        this.circuitBreakers = circuitBreakers;
        this.meters = meters;
    }

    @Override
    public GatewayFilter apply(Config config) {
        String route = config.getRouteId();
        var policy = settings.routes().get(route);
        Assert.notNull(policy, "Missing gateway protection configuration for route " + route);
        var breakerConfig = new SpringCloudCircuitBreakerFilterFactory.Config().setName(route)
                .setStatusCodes(Set.of("500", "502", "503", "504"));
        breakerConfig.setRouteId(route);
        // No fallback URI: keep the original exception and do not cache/replay request bodies.
        GatewayFilter protectedChain = policy.circuitBreaker().enabled()
                ? circuitBreakers.apply(breakerConfig) : (exchange, chain) -> chain.filter(exchange);
        return (exchange, chain) -> {
            exchange.getAttributes().put(CONTEXT_ATTRIBUTE, new RequestContext(route,
                    exchange.getRequest().getPath().value(), exchange.getRequest().getId(), policy.degradationEnabled()));
            Mono<Void> permission = policy.rateLimit().enabled()
                    ? limiter.acquire(route, policy.rateLimit()) : Mono.empty();
            return permission.then(Mono.defer(() -> protectedChain.filter(exchange, chain)))
                    .doOnSuccess(ignored -> meters.counter("mall.gateway.protection.requests",
                            "route", route, "outcome", "forwarded").increment());
        };
    }

    public record RequestContext(String route, String path, String requestId, boolean degradationEnabled) { }

    public static class Config implements HasRouteId {
        private String routeId;
        public String getRouteId() { return routeId; }
        public void setRouteId(String routeId) { this.routeId = routeId; }
    }
}
