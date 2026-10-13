package com.macro.mall.support;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Owns an isolated Redis process; never touches a developer's Redis database. */
public final class TestRedisServer implements AutoCloseable {
    private final Process process;
    private final int port;
    private final Path log;

    public TestRedisServer() throws IOException { this(0); }

    public TestRedisServer(int requestedPort) throws IOException {
        try (ServerSocket socket = new ServerSocket(requestedPort)) {
            port = socket.getLocalPort();
        }
        log = Files.createTempFile("mall-gateway-test-redis-", ".log");
        process = new ProcessBuilder(System.getProperty("test.redis.executable", "redis-server"),
                "--bind", "127.0.0.1", "--port", Integer.toString(port),
                "--save", "", "--appendonly", "no")
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                assertThat(process.isAlive()).withFailMessage("Redis startup failed: %s", log).isTrue();
                try (Socket socket = new Socket("127.0.0.1", port)) {
                    socket.setSoTimeout(500);
                    socket.getOutputStream().write("PING\r\n".getBytes(StandardCharsets.UTF_8));
                    assertThat(new String(socket.getInputStream().readNBytes(7), StandardCharsets.UTF_8))
                            .isEqualTo("+PONG\r\n");
                }
            });
        } catch (RuntimeException | Error e) {
            close();
            throw e;
        }
    }

    public int port() { return port; }

    @Override
    public void close() {
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted closing test Redis; log: " + log, e);
        }
    }
}
