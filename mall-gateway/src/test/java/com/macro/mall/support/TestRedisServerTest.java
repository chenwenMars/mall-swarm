package com.macro.mall.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestRedisServerTest {
    @TempDir
    Path directory;

    @Test
    void waitsForRedisWhenItsPortIsNotListeningYet() throws Exception {
        String redisExecutable = System.getProperty("test.redis.executable", "redis-server");
        withLauncher("sleep 0.8\nexec " + quote(redisExecutable) + " \"$@\"\n", () -> {
            try (TestRedisServer redis = new TestRedisServer()) {
                assertThat(redis.port()).isPositive();
            }
        });
    }

    @Test
    void readinessTimeoutRemainsVisibleAndStopsTheStartedProcess() throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String command = "exec " + quote(java) + " -cp " + quote(System.getProperty("java.class.path"))
                + " " + quote(SilentRedisProcess.class.getName()) + " \"$@\"\n";
        withLauncher(command, () -> {
            assertThatThrownBy(TestRedisServer::new).isInstanceOf(SocketTimeoutException.class);
            long pid = Long.parseLong(Files.readString(directory.resolve("process.pid")).trim());
            assertThat(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false))
                    .as("failed construction must stop the process it started").isFalse();
        });
    }

    private void withLauncher(String command, CheckedAction action) throws Exception {
        Path pidFile = directory.resolve("process.pid");
        Path launcher = directory.resolve("redis-launcher");
        Files.writeString(launcher, "#!/bin/sh\necho $$ > " + quote(pidFile.toString()) + "\n" + command);
        assertThat(launcher.toFile().setExecutable(true)).isTrue();
        String previous = System.getProperty("test.redis.executable");
        System.setProperty("test.redis.executable", launcher.toString());
        try {
            action.run();
        } finally {
            if (previous == null) System.clearProperty("test.redis.executable");
            else System.setProperty("test.redis.executable", previous);
            // Also clean up on RED: constructor failure means no AutoCloseable was returned.
            if (Files.exists(pidFile)) {
                long pid = Long.parseLong(Files.readString(pidFile).trim());
                ProcessHandle handle = ProcessHandle.of(pid).orElse(null);
                if (handle != null && handle.isAlive()) {
                    handle.descendants().forEach(ProcessHandle::destroyForcibly);
                    handle.destroyForcibly();
                    handle.onExit().get(5, TimeUnit.SECONDS);
                }
            }
        }
    }

    private static String quote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    @FunctionalInterface
    private interface CheckedAction {
        void run() throws Exception;
    }

    /** Test-only process that accepts a connection but never responds to PING. */
    public static class SilentRedisProcess {
        public static void main(String[] args) throws Exception {
            int port = 0;
            for (int i = 0; i < args.length - 1; i++) {
                if ("--port".equals(args[i])) port = Integer.parseInt(args[i + 1]);
            }
            try (ServerSocket server = new ServerSocket(port);
                 var connection = server.accept()) {
                Thread.sleep(10000);
            }
        }
    }
}
