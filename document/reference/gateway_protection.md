# 网关限流、熔断与降级

## 请求链路与边界

`Sa-Token 鉴权 → GatewayProtection → Redis 令牌桶 → Gateway 原生 CircuitBreaker → lb:// 下游服务`

- auth、admin、portal、search、demo 按路由隔离保护。Redis 额度由相同应用/环境的多个网关共享；Resilience4j 熔断状态由每台网关独立维护。
- 支付异步通知 `/mall-portal/alipay/notify` 优先匹配独立路由，不经过保护过滤器，保持现有转发和返回协议。
- 关闭 Gateway discovery locator 自动路由，继续通过 Nacos 服务发现和显式 `lb://` 路由转发。新增服务必须显式配置路由。
- 网关本地 Actuator 等端点不经过业务路由保护。下游直连流量也不在网关保护范围内。
- 显式关闭 Reactor Netty 默认的连接重试；不增加 Retry 过滤器、缓存业务结果、备用服务或写请求重放。超时只意味着网关停止等待，不证明下游事务已回滚；写入结果应通过业务查询核实。
- HTTP 200 中 `CommonResult.code=500` 仍按原样返回，不通过解析业务响应体统计熔断。鉴权先于保护，因此鉴权自身的 Redis 故障仍走现有鉴权异常处理。

## 配置与发布

规则样例在 `config/gateway/mall-gateway-dev.yaml` 和 `config/gateway/mall-gateway-prod.yaml`。它们对应网关当前导入的同名 Nacos Data ID；发布到网关实际使用的 namespace/group。

1. 先构建并验证网关制品。
2. 将所选环境样例中的 `mall.gateway.protection` 合并到对应 Nacos 配置，保留部署环境已有的 Redis、日志等配置，不直接覆盖整份文件。
3. 启动新版网关。缺少规则或规则不合法时启动失败，不自动填补阈值。
4. 执行正常请求、超额请求、下游超时及恢复演练；检查 HTTP 状态、响应 JSON、日志和指标。
5. 后续修改保护规则必须滚动重启网关。Nacos 刷新事件不会修改已加载的保护快照或重建熔断器。滚动期间不同实例可能短暂使用不同版本的规则。

命名空间默认分别为 `mall-gateway:dev` 和 `mall-gateway:prod`。部署多套相同 profile 的环境时应使用不同 namespace，避免共享限额。相同环境的所有网关实例须保持 namespace 和限流参数一致。

```yaml
mall:
  gateway:
    protection:
      namespace: mall-gateway:dev
      redis-timeout: 1s
      routes:
        mall-auth:
          rate-limit:
            enabled: true
            replenish-rate: 100
            burst-capacity: 200
            requested-tokens: 1
          degradation-enabled: true
          circuit-breaker:
            enabled: true
            sliding-window-size: 20
            minimum-number-of-calls: 10
            failure-rate-threshold: 50
            wait-duration-in-open-state: 10s
            permitted-number-of-calls-in-half-open-state: 3
            timeout: 5s
```

其余四条路由也必须配置；完整文件用 YAML anchor 复用初始规则。admin/search 超时为 30 秒，其余为 5 秒。以上仅为联调初值，生产值需要根据容量和业务耗时确定，尤其要评估导入、上传等长耗时操作。

- `rate-limit.enabled=false`：跳过该路由的 Redis 限流。
- `circuit-breaker.enabled=false`：跳过该路由的熔断及 TimeLimiter。
- `degradation-enabled=false`：仍执行限流/熔断并记录日志与指标，但已识别异常交回原有 WebFlux 错误处理，不输出统一 CommonResult。
- 三个开关相互独立，均在重启后生效；关闭功能不会删除 Redis 桶。
- 限流采用令牌桶，并非严格滑动窗口 QPS：Redis 每秒补充令牌，允许桶容量内的突发。`burst-capacity >= replenish-rate`，且 `1 <= requested-tokens <= burst-capacity`。
- 熔断按最近 N 次调用计数，达到最小调用数且失败率不低于阈值后开启；等待时间到后，由后续请求触发半开探测。不开启额外的慢调用比例策略。

## Redis 与熔断实现

限流调用 `ReactiveStringRedisTemplate`，复用 Gateway 4.3.4 提供的 `redisRequestRateLimiterScript`，使用 Redis TIME 统一时钟。两个键为：

```text
mall:gateway:rate:{mall-gateway:dev:mall-auth}:tokens
mall:gateway:rate:{mall-gateway:dev:mall-auth}:timestamp
```

花括号内一致，保证 Redis Cluster 将两个键分配到同一槽。脚本原子计算令牌并设置过期时间。

Gateway 内置 `RedisRateLimiter` 在 Redis 出错时会放行；本实现直接调用其脚本，保留异常并返回 503。连接/执行超时由 `redis-timeout` 限定。不应在这些路由上再叠加默认 RedisRateLimiter。

熔断复用原生 `SpringCloudCircuitBreakerResilience4JFilterFactory`，由不可变启动配置统一创建 CircuitBreaker 和 TimeLimiter。下游 HTTP 500/502/503/504、连接失败和超时进入失败统计。429 及限流 Redis 故障发生在熔断链之外。熔断不设置 fallback URI，因此不为降级缓存或重放请求体。

超时作用范围沿用原生 Gateway 转发过滤链，主要约束连接和等待下游响应头；已经提交的响应以及后续流式响应体不能再被改写成 JSON 错误。

## 返回、日志与指标

| 场景 | HTTP / code | message |
| --- | --- | --- |
| 额度耗尽 | 429 | 请求过于频繁，请稍后再试 |
| 限流 Redis 故障 | 503 | 网关限流服务暂不可用 |
| 熔断开启 | 503 | 服务熔断中，请稍后再试 |
| 无可用实例 | 503 | 服务暂无可用实例 |
| 连接失败 | 503 | 暂时无法连接目标服务 |
| 下游 500/502/503 | 503 | 目标服务暂不可用 |
| 超时或下游 504 | 504 | 目标服务响应超时 |

```json
{"code":503,"message":"目标服务暂不可用","data":null}
```

已识别失败使用 HTTP 错误状态，不伪造业务成功；不向客户端返回堆栈或内部地址。保护异常处理器实现 WebExceptionHandler，保留 Spring Boot 默认错误处理器。未知程序异常继续抛出，已提交响应不覆盖。

拒绝请求和熔断状态变化记录 WARN；Redis/下游故障记录 ERROR 和原始异常。请求日志包含 route、原始 path、requestId、reason，不记录请求体、Authorization 或查询字符串。

在原有 Actuator 访问控制范围内查看：

```text
/actuator/metrics/mall.gateway.protection.requests
/actuator/metrics/resilience4j.circuitbreaker.state
```

保护计数使用 route/outcome 标签，outcome 为 forwarded 或明确错误类型。forwarded 表示转发过滤链完成，不等价于业务成功。结合 Redis 故障计数、熔断开启状态和网关 HTTP 指标设置告警。

## 本地验证与故障演练

使用 JDK 17、Maven 和本机 `redis-server`。测试自动启动独立临时 Redis 进程、两个网关实例和 HTTP 下游，使用随机端口，不连接开发环境 Redis/Nacos，不使用 Docker。缺少 redis-server 时测试明确失败；非 PATH 安装可增加 `-Dtest.redis.executable=/absolute/path/to/redis-server`。

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
mvn -pl mall-gateway -am test -DskipTests=false -Ddocker.skip=true
mvn -pl mall-gateway -am package -DskipTests -Ddocker.skip=true
```

测试包含正常 POST 转发、共享限额/补充/隔离、Redis 脚本错误和停机、HTTP 错误/超时、熔断开启/半开/恢复、支付通知豁免、鉴权顺序、开关、配置快照和非法配置。测试日志中的 WRONGTYPE、连接拒绝、超时是故障注入的预期证据。

在专用联调环境中可降低 mall-auth 的 `replenish-rate`/`burst-capacity`，重启后并发访问其真实接口，观察 429；使用无副作用查询接口停止对应服务，观察 503 与熔断事件；恢复服务并等待探测成功。停止限流 Redis 时，用无需鉴权的业务入口验证明确失败；不要在生产环境执行停机演练。

参考：[Gateway 限流源码](https://github.com/spring-cloud/spring-cloud-gateway/blob/v4.3.4/spring-cloud-gateway-server/src/main/java/org/springframework/cloud/gateway/filter/ratelimit/RedisRateLimiter.java)、[原生 CircuitBreaker](https://docs.spring.io/spring-cloud-gateway/reference/4.3/spring-cloud-gateway-server-webflux/gatewayfilter-factories/circuitbreaker-filter-factory.html)。
