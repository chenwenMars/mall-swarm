package com.macro.mall.protection;

import com.macro.mall.common.api.IErrorCode;

public enum GatewayProtectionError implements IErrorCode {
    RATE_LIMITED(429, "请求过于频繁，请稍后再试"),
    REDIS_UNAVAILABLE(503, "网关限流服务暂不可用"),
    CIRCUIT_OPEN(503, "服务熔断中，请稍后再试"),
    NO_INSTANCE(503, "服务暂无可用实例"),
    CONNECTION_FAILURE(503, "暂时无法连接目标服务"),
    UPSTREAM_FAILURE(503, "目标服务暂不可用"),
    UPSTREAM_TIMEOUT(504, "目标服务响应超时");

    private final long code;
    private final String message;

    GatewayProtectionError(long code, String message) {
        this.code = code;
        this.message = message;
    }

    public long getCode() { return code; }
    public String getMessage() { return message; }
}
