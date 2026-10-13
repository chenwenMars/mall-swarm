package com.macro.mall.protection;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayProtectionExceptionHandlerTest {
    private final GatewayProtectionExceptionHandler handler =
            new GatewayProtectionExceptionHandler(new ObjectMapper(), new SimpleMeterRegistry());

    private MockServerWebExchange exchange() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/mall-search/test"));
        exchange.getAttributes().put(GatewayProtectionGatewayFilterFactory.CONTEXT_ATTRIBUTE,
                new GatewayProtectionGatewayFilterFactory.RequestContext("mall-search", "/mall-search/test", "test-id", true));
        return exchange;
    }

    @Test
    void committedResponseIsNeverRewritten() {
        var exchange = exchange();
        exchange.getResponse().setComplete().block();
        var failure = new ConnectException("unavailable");
        StepVerifier.create(handler.handle(exchange, failure)).expectErrorMatches(error -> error == failure).verify();
    }

    @Test
    void unknownFailureAndUnprotectedRequestsRemainOnOriginalErrorChain() {
        var unknown = new IllegalStateException("bug");
        StepVerifier.create(handler.handle(exchange(), unknown)).expectErrorMatches(error -> error == unknown).verify();
        var unprotected = MockServerWebExchange.from(MockServerHttpRequest.get("/alipay/notify"));
        var connectionFailure = new ConnectException("secret upstream address");
        StepVerifier.create(handler.handle(unprotected, connectionFailure))
                .expectErrorMatches(error -> error == connectionFailure).verify();
    }

    @Test
    void noInstanceProduces503AndStaleRepresentationHeadersAreRemoved() {
        var exchange = exchange();
        exchange.getResponse().getHeaders().setContentType(MediaType.TEXT_HTML);
        exchange.getResponse().getHeaders().setContentLength(9999);
        exchange.getResponse().getHeaders().set(HttpHeaders.CONTENT_ENCODING, "gzip");
        handler.handle(exchange, new NotFoundException("secret service address")).block();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(503);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(exchange.getResponse().getHeaders()).doesNotContainKeys(HttpHeaders.CONTENT_ENCODING);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("\"code\":503")
                .doesNotContain("secret");
    }
}
