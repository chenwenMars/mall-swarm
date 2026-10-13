package com.macro.mall;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayRouteConfigurationTest {
    @Test
    void onlyExplicitRoutesAreExposedAndPaymentCallbackHasPriority() throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader().load("gateway", new ClassPathResource("application.yml"))
                .forEach(environment.getPropertySources()::addLast);
        assertThat(environment.getProperty("spring.cloud.gateway.server.webflux.discovery.locator.enabled"))
                .isEqualTo("false");
        GatewayProperties properties = Binder.get(environment)
                .bind("spring.cloud.gateway.server.webflux", Bindable.of(GatewayProperties.class)).get();
        assertThat(properties.getRoutes()).hasSize(6);
        var callback = properties.getRoutes().get(0);
        assertThat(callback.getId()).isEqualTo("mall-portal-payment-notify");
        assertThat(callback.getPredicates().get(0).getArgs().values())
                .containsExactly("/mall-portal/alipay/notify");
        assertThat(callback.getFilters()).extracting("name").containsExactly("StripPrefix");
        assertThat(callback.getOrder()).isLessThan(properties.getRoutes().get(1).getOrder());
        properties.getRoutes().subList(1, 6).forEach(route ->
                assertThat(route.getFilters()).extracting("name")
                        .containsExactly("GatewayProtection", "StripPrefix"));
    }
}
