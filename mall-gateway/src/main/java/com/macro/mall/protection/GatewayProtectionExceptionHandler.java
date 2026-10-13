package com.macro.mall.protection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.common.api.CommonResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.factory.SpringCloudCircuitBreakerFilterFactory.CircuitBreakerStatusCodeException;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.cloud.gateway.support.ServiceUnavailableException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.PrematureCloseException;

import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

@Component
@Order(-2)
public class GatewayProtectionExceptionHandler implements WebExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayProtectionExceptionHandler.class);
    private final ObjectMapper mapper;
    private final MeterRegistry meters;

    public GatewayProtectionExceptionHandler(ObjectMapper mapper, MeterRegistry meters) {
        this.mapper = mapper;
        this.meters = meters;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable exception) {
        GatewayProtectionGatewayFilterFactory.RequestContext context =
                exchange.getAttribute(GatewayProtectionGatewayFilterFactory.CONTEXT_ATTRIBUTE);
        GatewayProtectionError error = classify(exception);
        if (context == null || error == null) {
            return Mono.error(exception);
        }
        meters.counter("mall.gateway.protection.requests", "route", context.route(), "outcome", error.name()).increment();
        if (error == GatewayProtectionError.RATE_LIMITED || error == GatewayProtectionError.CIRCUIT_OPEN) {
            LOGGER.warn("Gateway request rejected route={} path={} requestId={} reason={}",
                    context.route(), context.path(), context.requestId(), error);
        } else {
            LOGGER.error("Gateway request failed route={} path={} requestId={} reason={}",
                    context.route(), context.path(), context.requestId(), error, exception);
        }
        if (!context.degradationEnabled() || exchange.getResponse().isCommitted()) {
            return Mono.error(exception);
        }
        byte[] body;
        try {
            body = mapper.writeValueAsBytes(CommonResult.failed(error));
        } catch (JsonProcessingException serializationError) {
            serializationError.addSuppressed(exception);
            return Mono.error(serializationError);
        }
        var response = exchange.getResponse();
        // Discard representation headers inherited from an upstream error body.
        response.getHeaders().remove(HttpHeaders.CONTENT_ENCODING);
        response.getHeaders().remove(HttpHeaders.TRANSFER_ENCODING);
        response.getHeaders().remove(HttpHeaders.CONTENT_LENGTH);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().setCacheControl("no-store");
        response.setStatusCode(HttpStatusCode.valueOf((int) error.getCode()));
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    static GatewayProtectionError classify(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof GatewayProtectionException protection) return protection.getError();
            if (cause instanceof CircuitBreakerStatusCodeException status) {
                return status.getStatusCode().value() == 504 ? GatewayProtectionError.UPSTREAM_TIMEOUT
                        : GatewayProtectionError.UPSTREAM_FAILURE;
            }
            if (cause instanceof TimeoutException || cause instanceof ReadTimeoutException
                    || cause instanceof ConnectTimeoutException) return GatewayProtectionError.UPSTREAM_TIMEOUT;
            if (cause instanceof ServiceUnavailableException) return GatewayProtectionError.CIRCUIT_OPEN;
            if (cause instanceof NotFoundException) return GatewayProtectionError.NO_INSTANCE;
            if (cause instanceof SocketException || cause instanceof UnknownHostException
                    || cause instanceof PrematureCloseException) return GatewayProtectionError.CONNECTION_FAILURE;
        }
        return null;
    }
}
