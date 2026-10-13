package com.macro.mall;

import com.macro.mall.protection.GatewayProtectionSettings;
import com.macro.mall.support.TestRedisServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.reactive.context.ReactiveWebServerApplicationContext;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.net.ServerSocket;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@ExtendWith(OutputCaptureExtension.class)
class MallGatewayApplicationTests {
    static TestRedisServer redis;
    static DisposableServer downstream;
    static ReactiveWebServerApplicationContext gateway;
    static ReactiveWebServerApplicationContext secondGateway;
    static WebTestClient client;
    static WebTestClient secondClient;
    static final AtomicInteger calls = new AtomicInteger();

    @BeforeAll
    static void start() throws Exception {
        redis = new TestRedisServer();
        downstream = HttpServer.create().host("127.0.0.1").port(0)
                .handle((request, response) -> {
                    calls.incrementAndGet();
                    String path = request.fullPath();
                    if (path.equals("/slow")) {
                        return Mono.delay(Duration.ofSeconds(2)).then(response.sendString(Mono.just("late")).then());
                    }
                    if (path.equals("/disconnect")) {
                        response.withConnection(connection -> connection.dispose());
                        return Mono.empty();
                    }
                    if (path.contains("/status/")) {
                        response.status(Integer.parseInt(path.substring(path.indexOf("/status/") + "/status/".length())));
                        return response.header("Content-Type", "application/json")
                                .sendString(Mono.just("{\"original\":true}"));
                    }
                    if (path.equals("/business-error")) {
                        return response.header("Content-Type", "application/json")
                                .sendString(Mono.just("{\"code\":500,\"message\":\"业务失败\",\"data\":null}"));
                    }
                    if (path.equals("/alipay/notify")) return response.sendString(Mono.just("success"));
                    return request.receive().aggregate().asString().defaultIfEmpty("")
                            .flatMap(body -> response.header("X-Downstream", "real")
                                    .sendString(Mono.just(request.method().name() + " " + request.uri() + " " + body)).then());
                }).bindNow();
        gateway = startGateway();
        secondGateway = startGateway("--mall.gateway.protection.routes.mall-portal.rate-limit.enabled=false",
                "--mall.gateway.protection.routes.mall-portal.circuit-breaker.enabled=false");
        client = client(gateway);
        secondClient = client(secondGateway);
    }

    static ReactiveWebServerApplicationContext startGateway(String... extra) throws Exception {
        List<String> args = new ArrayList<>(List.of(
                "--logging.level.root=WARN", "--logging.level.com.macro.mall.protection=INFO",
                "--spring.profiles.active=test", "--spring.config.import=", "--server.port=0",
                "--spring.cloud.nacos.discovery.enabled=false", "--spring.cloud.nacos.config.enabled=false",
                "--spring.cloud.nacos.config.import-check.enabled=false", "--spring.cloud.discovery.enabled=false",
                "--spring.boot.admin.client.enabled=false", "--knife4j.gateway.enabled=false",
                "--spring.data.redis.host=127.0.0.1", "--spring.data.redis.port=" + redis.port(),
                "--spring.data.redis.timeout=300ms", "--spring.data.redis.connect-timeout=300ms",
                "--mall.gateway.protection.namespace=mall-gateway:test",
                "--mall.gateway.protection.redis-timeout=300ms",
                "--mall.gateway.protection.routes.mall-admin.rate-limit.enabled=false",
                "--mall.gateway.protection.routes.mall-admin.circuit-breaker.enabled=false",
                "--mall.gateway.protection.routes.mall-demo.rate-limit.enabled=false",
                "--mall.gateway.protection.routes.mall-demo.degradation-enabled=false"));
        for (String route : List.of("mall-auth", "mall-portal")) {
            args.add("--mall.gateway.protection.routes." + route + ".rate-limit.replenish-rate=1");
            args.add("--mall.gateway.protection.routes." + route + ".rate-limit.burst-capacity=20");
            args.add("--mall.gateway.protection.routes." + route + ".rate-limit.requested-tokens=10");
        }
        for (String route : List.of("mall-auth", "mall-admin", "mall-portal", "mall-search", "mall-demo")) {
            String prefix = "--mall.gateway.protection.routes." + route + ".circuit-breaker.";
            args.add(prefix + "sliding-window-size=4");
            args.add(prefix + "minimum-number-of-calls=4");
            args.add(prefix + "wait-duration-in-open-state=500ms");
            args.add(prefix + "permitted-number-of-calls-in-half-open-state=2");
            args.add(prefix + "timeout=500ms");
        }
        // Copy the whole list because Spring replaces lists across property sources.
        var yaml = new org.springframework.boot.env.YamlPropertySourceLoader()
                .load("routes", new org.springframework.core.io.ClassPathResource("application.yml"));
        var source = (org.springframework.core.env.EnumerablePropertySource<?>) yaml.get(0);
        for (String key : source.getPropertyNames()) {
            if (key.startsWith("spring.cloud.gateway.server.webflux.routes[")) {
                Object value = key.endsWith(".uri") ? "http://127.0.0.1:" + downstream.port() : source.getProperty(key);
                args.add("--" + key + "=" + value);
            }
        }
        args.addAll(List.of(extra));
        return (ReactiveWebServerApplicationContext) new SpringApplicationBuilder(MallGatewayApplication.class,
                FaultConfiguration.class).run(args.toArray(String[]::new));
    }

    static WebTestClient client(ReactiveWebServerApplicationContext context) {
        return WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + context.getWebServer().getPort())
                .responseTimeout(Duration.ofSeconds(10)).build();
    }

    @BeforeEach
    void resetState() {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            try (var connection = gateway.getBean(StringRedisTemplate.class).getConnectionFactory().getConnection()) {
                connection.serverCommands().flushDb();
            }
        });
        gateway.getBean(CircuitBreakerRegistry.class).getAllCircuitBreakers().forEach(CircuitBreaker::reset);
        secondGateway.getBean(CircuitBreakerRegistry.class).getAllCircuitBreakers().forEach(CircuitBreaker::reset);
        calls.set(0);
    }

    @AfterAll
    static void stop() {
        if (secondGateway != null) secondGateway.close();
        if (gateway != null) gateway.close();
        if (downstream != null) downstream.disposeNow();
        if (redis != null) redis.close();
    }

    void ok(WebTestClient target, String path) {
        target.get().uri(path).exchange().expectStatus().isOk().expectBody(String.class);
    }

    void failure(String path, int status) {
        client.get().uri(path).exchange().expectStatus().isEqualTo(status)
                .expectBody().jsonPath("$.code").isEqualTo(status).jsonPath("$.data").isEmpty();
    }

    CircuitBreaker breaker(String route) { return gateway.getBean(CircuitBreakerRegistry.class).circuitBreaker(route); }

    @Test
    void twoGatewaysShareBudgetAndRouteBucketsAreIsolated(CapturedOutput output) {
        ok(client, "/mall-auth/echo");
        ok(secondClient, "/mall-auth/echo");
        int before = calls.get();
        failure("/mall-auth/echo", 429);
        assertThat(calls.get()).isEqualTo(before);
        ok(client, "/mall-portal/product/echo");
        assertThat(breaker("mall-auth").getMetrics().getNumberOfFailedCalls()).isZero();
        assertThat(output).contains("route=mall-auth", "path=/mall-auth/echo", "reason=RATE_LIMITED");
        assertThat(gateway.getBean(MeterRegistry.class).get("mall.gateway.protection.requests")
                .tags("route", "mall-auth", "outcome", "RATE_LIMITED").counter().count()).isPositive();
    }

    @Test
    void tokenBucketRefillsUsingRedisTime() {
        ok(client, "/mall-auth/echo");
        ok(client, "/mall-auth/echo");
        failure("/mall-auth/echo", 429);
        await().pollInterval(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(12)).untilAsserted(() ->
                client.get().uri("/mall-auth/echo").exchange().expectStatus().isOk().expectBody(String.class));
    }

    @Test
    void forwardsMethodPathQueryAndBodyWithoutRetry() {
        client.post().uri(builder -> builder.path("/mall-search/echo").queryParam("q", "a b").build()).bodyValue("order-payload")
                .exchange().expectStatus().isOk().expectHeader().valueEquals("X-Downstream", "real")
                .expectBody(String.class).isEqualTo("POST /echo?q=a%20b order-payload");
        assertThat(calls.get()).isEqualTo(1);
        client.post().uri("/mall-search/status/500").bodyValue("write-once")
                .exchange().expectStatus().isEqualTo(503).expectBody().jsonPath("$.code").isEqualTo(503);
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void opensThenRecoversAndFailedHalfOpenProbeReopens(CapturedOutput output) {
        for (int i = 0; i < 4; i++) failure("/mall-search/status/500", 503);
        assertThat(breaker("mall-search").getState()).isEqualTo(CircuitBreaker.State.OPEN);
        int before = calls.get();
        failure("/mall-search/echo", 503);
        assertThat(calls.get()).isEqualTo(before);
        ok(client, "/mall-auth/echo");
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            client.get().uri("/mall-search/status/500").exchange().expectStatus().isEqualTo(503).expectBody();
            assertThat(calls.get()).isGreaterThan(before + 1);
        });
        // Complete the two failing half-open samples.
        failure("/mall-search/status/500", 503);
        assertThat(breaker("mall-search").getState()).isEqualTo(CircuitBreaker.State.OPEN);
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> ok(client, "/mall-search/echo"));
        ok(client, "/mall-search/echo");
        assertThat(breaker("mall-search").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(output).contains("Gateway circuit transition route=mall-search", "reason=CIRCUIT_OPEN");
        assertThat(gateway.getBean(MeterRegistry.class).find("resilience4j.circuitbreaker.state").meters()).isNotEmpty();
    }

    @Test
    void timeoutsAndHttp504Return504() {
        failure("/mall-search/slow", 504);
        failure("/mall-search/status/504", 504);
        assertThat(breaker("mall-search").getMetrics().getNumberOfFailedCalls()).isEqualTo(2);
    }

    @Test
    void businessFailureAndClientErrorsDoNotTripBreaker() {
        for (int i = 0; i < 5; i++) {
            client.get().uri("/mall-search/business-error").exchange().expectStatus().isOk()
                    .expectBody().jsonPath("$.code").isEqualTo(500);
            client.get().uri("/mall-search/status/400").exchange().expectStatus().isBadRequest()
                    .expectBody().jsonPath("$.original").isEqualTo(true);
        }
        assertThat(breaker("mall-search").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker("mall-search").getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    void paymentCallbackBypassesExhaustedBudgetAndOpenPortalCircuit() {
        ok(client, "/mall-portal/product/echo");
        ok(client, "/mall-portal/product/echo");
        failure("/mall-portal/product/echo", 429);
        breaker("mall-portal").transitionToOpenState();
        client.post().uri("/mall-portal/alipay/notify").bodyValue("signed-notification")
                .exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("success");
    }

    @Test
    void authenticationStillPrecedesProtectionAndUnlistedPathsAreNotExposed() {
        client.get().uri("/mall-admin/private").exchange().expectBody().jsonPath("$.code").isEqualTo(401);
        assertThat(calls.get()).isZero();
        assertThat(breaker("mall-admin").getMetrics().getNumberOfBufferedCalls()).isZero();
        client.get().uri("/mall-monitor/actuator/health").exchange().expectStatus().isNotFound().expectBody();
        assertThat(calls.get()).isZero();
    }

    @Test
    void disabledRateLimitAndCircuitBreakerPreserveUpstreamErrors() {
        for (int i = 0; i < 6; i++) {
            secondClient.get().uri("/mall-portal/product/status/500").exchange().expectStatus().isEqualTo(500).expectBody();
            client.get().uri("/mall-admin/admin/login").exchange().expectStatus().isOk().expectBody();
        }
        assertThat(calls.get()).isEqualTo(12);
        assertThat(secondGateway.getBean(CircuitBreakerRegistry.class).circuitBreaker("mall-portal")
                .getMetrics().getNumberOfBufferedCalls()).isZero();
    }

    @Test
    void disablingDegradationRetainsOriginalErrorHandling() {
        client.get().uri("/mall-demo/status/500").exchange().expectStatus().is5xxServerError()
                .expectBody().jsonPath("$.code").doesNotExist().jsonPath("$.status").isEqualTo(500);
    }

    @Test
    void redisScriptFailureRejectsWithoutCallingDownstreamOrRecordingCircuitFailure(CapturedOutput output) {
        gateway.getBean(StringRedisTemplate.class).opsForList()
                .rightPush("mall:gateway:rate:{mall-gateway:test:mall-auth}:tokens", "wrong-type");
        failure("/mall-auth/echo", 503);
        assertThat(calls.get()).isZero();
        assertThat(breaker("mall-auth").getMetrics().getNumberOfBufferedCalls()).isZero();
        assertThat(output).contains("reason=REDIS_UNAVAILABLE", "WRONGTYPE");
    }

    @Test
    void redisOutageRejectsRequestsAndPaymentCallbackStillForwards() throws Exception {
        int port = redis.port();
        redis.close();
        try {
            failure("/mall-auth/echo", 503);
            assertThat(calls.get()).isZero();
            client.post().uri("/mall-portal/alipay/notify").exchange().expectStatus().isOk()
                    .expectBody(String.class).isEqualTo("success");
        } finally {
            redis = new TestRedisServer(port);
        }
    }

    @Test
    void connectionTerminationDoesNotAutomaticallyReplayRequest() {
        client.post().uri("/mall-search/disconnect").bodyValue("once")
                .exchange().expectStatus().isEqualTo(503).expectBody().jsonPath("$.code").isEqualTo(503);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void transportAutomaticRetryIsDisabled() {
        assertThat(gateway.getBean(reactor.netty.http.client.HttpClient.class).configuration().isRetryDisabled()).isTrue();
    }

    @Test
    void unknownProgrammingFailureRemains500() {
        client.get().uri("/mall-search/unknown-error").exchange().expectStatus().isEqualTo(500)
                .expectBody().jsonPath("$.code").doesNotExist();
        assertThat(calls.get()).isZero();
    }

    @Test
    void environmentRefreshDoesNotChangeProtectionSnapshot() {
        var original = gateway.getBean(GatewayProtectionSettings.class);
        String key = "mall.gateway.protection.routes.mall-auth.rate-limit.enabled";
        gateway.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test-refresh", Map.of(key, "false")));
        try {
            gateway.publishEvent(new EnvironmentChangeEvent(Set.of(key)));
            assertThat(gateway.getBean(GatewayProtectionSettings.class)).isSameAs(original);
            ok(client, "/mall-auth/echo");
            ok(client, "/mall-auth/echo");
            failure("/mall-auth/echo", 429);
        } finally {
            gateway.getEnvironment().getPropertySources().remove("test-refresh");
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FaultConfiguration {
        @Bean
        @Order(100)
        GlobalFilter controlledFailure() {
            return (exchange, chain) -> exchange.getRequest().getPath().value().endsWith("/unknown-error")
                    ? Mono.error(new IllegalStateException("deliberate test-only programming failure")) : chain.filter(exchange);
        }
    }
}
