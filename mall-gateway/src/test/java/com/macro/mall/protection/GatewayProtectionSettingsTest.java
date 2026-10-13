package com.macro.mall.protection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayProtectionSettingsTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(GatewayProtectionConfiguration.class)
            .withPropertyValues("spring.application.name=mall-gateway")
            .withBean(org.springframework.cloud.gateway.config.GatewayProperties.class,
                    org.springframework.cloud.gateway.config.GatewayProperties::new)
            .withInitializer(context -> {
                try {
                    new YamlPropertySourceLoader().load("test-rules", new ClassPathResource("application-test.yml"))
                            .forEach(context.getEnvironment().getPropertySources()::addLast);
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });

    @ParameterizedTest
    @ValueSource(strings = {
            "namespace=bad{namespace}", "redis-timeout=0ms",
            "routes.mall-auth.rate-limit.replenish-rate=0",
            "routes.mall-auth.rate-limit.burst-capacity=1",
            "routes.mall-auth.rate-limit.requested-tokens=201",
            "routes.mall-auth.circuit-breaker.sliding-window-size=0",
            "routes.mall-auth.circuit-breaker.minimum-number-of-calls=21",
            "routes.mall-auth.circuit-breaker.failure-rate-threshold=101",
            "routes.mall-auth.circuit-breaker.wait-duration-in-open-state=0s",
            "routes.mall-auth.circuit-breaker.permitted-number-of-calls-in-half-open-state=0",
            "routes.mall-auth.circuit-breaker.timeout=0s"
    })
    void invalidRulesFailAtStartup(String property) {
        runner.withPropertyValues("mall.gateway.protection." + property)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void requiredConfigurationCannotSilentlyFallBackToDefaults() {
        new ApplicationContextRunner().withUserConfiguration(GatewayProtectionConfiguration.class)
                .withBean(org.springframework.cloud.gateway.config.GatewayProperties.class,
                        org.springframework.cloud.gateway.config.GatewayProperties::new)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("Missing mall.gateway.protection configuration");
                });
    }

    @Test
    void routeWithoutProtectionPolicyFailsAtStartup() {
        runner.withAllowBeanDefinitionOverriding(true).withBean("gatewayProperties", org.springframework.cloud.gateway.config.GatewayProperties.class, () -> {
            var properties = new org.springframework.cloud.gateway.config.GatewayProperties();
            var route = new org.springframework.cloud.gateway.route.RouteDefinition();
            route.setId("unconfigured-service");
            route.setUri(java.net.URI.create("http://localhost:9999"));
            route.setFilters(java.util.List.of(new org.springframework.cloud.gateway.filter.FilterDefinition("GatewayProtection")));
            properties.setRoutes(java.util.List.of(route));
            return properties;
        }).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("Missing gateway protection configuration for route unconfigured-service");
        });
    }

    @Test
    void publishedPoliciesCannotBeMutated() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var settings = context.getBean(GatewayProtectionSettings.class);
            assertThatThrownBy(() -> settings.routes().clear()).isInstanceOf(UnsupportedOperationException.class);
        });
    }
}
