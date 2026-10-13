package com.macro.mall.protection;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;

@Configuration(proxyBeanMethods = false)
public class GatewayProtectionConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayProtectionConfiguration.class);

    @Bean
    public GatewayProtectionSettings gatewayProtectionSettings(Environment environment, GatewayProperties gatewayProperties) {
        var settings = Binder.get(environment).bind("mall.gateway.protection", Bindable.of(GatewayProtectionSettings.class))
                .orElseThrow(() -> new IllegalStateException("Missing mall.gateway.protection configuration"));
        gatewayProperties.getRoutes().stream()
                .filter(route -> route.getFilters().stream().anyMatch(filter -> "GatewayProtection".equals(filter.getName())))
                .forEach(route -> Assert.isTrue(settings.routes().containsKey(route.getId()),
                        "Missing gateway protection configuration for route " + route.getId()));
        return settings;
    }

    @Bean
    public HttpClientCustomizer disableGatewayTransportRetry() {
        return client -> client.disableRetry(true);
    }

    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> gatewayCircuitBreakers(GatewayProtectionSettings settings) {
        return factory -> settings.routes().forEach((route, policy) -> {
            var config = policy.circuitBreaker();
            var circuit = CircuitBreakerConfig.custom()
                    .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                    .slidingWindowSize(config.slidingWindowSize())
                    .minimumNumberOfCalls(config.minimumNumberOfCalls())
                    .failureRateThreshold(config.failureRateThreshold())
                    .waitDurationInOpenState(config.waitDurationInOpenState())
                    .permittedNumberOfCallsInHalfOpenState(config.permittedNumberOfCallsInHalfOpenState())
                    // The TimeLimiter terminates calls before they could become slow-call samples.
                    .slowCallDurationThreshold(config.timeout().plusSeconds(60))
                    .build();
            var timer = TimeLimiterConfig.custom().timeoutDuration(config.timeout()).build();
            factory.configure(builder -> builder.circuitBreakerConfig(circuit).timeLimiterConfig(timer), route);
            factory.getTimeLimiterRegistry().timeLimiter(route, timer);
            factory.getCircuitBreakerRegistry().circuitBreaker(route, circuit).getEventPublisher()
                    .onStateTransition(event -> LOGGER.warn("Gateway circuit transition route={} transition={}",
                            route, event.getStateTransition()));
        });
    }
}
