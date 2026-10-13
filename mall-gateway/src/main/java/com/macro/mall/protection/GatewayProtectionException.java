package com.macro.mall.protection;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

public class GatewayProtectionException extends ResponseStatusException {
    private final GatewayProtectionError error;

    public GatewayProtectionException(GatewayProtectionError error, Throwable cause) {
        super(HttpStatusCode.valueOf((int) error.getCode()), error.getMessage(), cause);
        this.error = error;
    }

    public GatewayProtectionError getError() { return error; }
}
