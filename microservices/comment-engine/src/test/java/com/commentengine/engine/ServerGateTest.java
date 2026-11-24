package com.commentengine.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ServerGateTest {

    private final ExecutorService pool = Executors.newCachedThreadPool();

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
    }

    private static Instant in(Duration d) {
        return Instant.now().plus(d);
    }

    /** 줄에 n명이 설 때까지 기다린다 */
    private static void awaitWaiting(ServerGate gate, int n) throws InterruptedException {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (gate.waitingCount() < n) {
            if (System.nanoTime() > until) {
                throw new AssertionError("줄에 " + n + "명이 서지 않음 (지금 " + gate.waitingCount() + ")");
            }
            Thread.sleep(5);
        }
    }

    @Test
    void 동시에_2개가_들어오면_하나는_기다린다() throws Exception {
        ServerGate gate = new ServerGate(1);

        assertThat(gate.enter(GenerationKind.LIGHT, in(Duration.ofSeconds(5)))).isTrue();
        Future<Boolean> second = pool.submit(() -> gate.enter(GenerationKind.LIGHT, in(Duration.ofSeconds(5))));

        awaitWaiting(gate, 1);
        assertThat(second.isDone()).isFalse();
        assertThat(gate.runningCount()).isEqualTo(1);

        gate.leave(); // 자리를 넘긴다
        assertThat(second.get(5, TimeUnit.SECONDS)).isTrue();
        assertThat(gate.waitingCount()).isZero();
        assertThat(gate.runningCount()).isEqualTo(1);

        gate.leave();
        assertThat(gate.runningCount()).isZero();
    }

    @Test
    void 줄에_light와_section이_있으면_light가_먼저_들어간다() throws Exception {
        ServerGate gate = new ServerGate(1);
        List<GenerationKind> entered = new CopyOnWriteArrayList<>();

        assertThat(gate.enter(GenerationKind.LIGHT, in(Duration.ofSeconds(5)))).isTrue();

        // section이 먼저 와서 줄을 서고, light가 나중에 온다
        Future<?> section = pool.submit(() -> enterAndRecord(gate, GenerationKind.SECTION, entered));
        awaitWaiting(gate, 1);
        Future<?> light = pool.submit(() -> enterAndRecord(gate, GenerationKind.LIGHT, entered));
        awaitWaiting(gate, 2);

        gate.leave();
        light.get(5, TimeUnit.SECONDS);
        section.get(5, TimeUnit.SECONDS);

        assertThat(entered).containsExactly(GenerationKind.LIGHT, GenerationKind.SECTION);
        assertThat(gate.runningCount()).isZero();
    }

    private static Void enterAndRecord(ServerGate gate, GenerationKind kind, List<GenerationKind> entered)
            throws InterruptedException {
        if (gate.enter(kind, in(Duration.ofSeconds(5)))) {
            entered.add(kind);
            gate.leave();
        }
        return null;
    }

    @Test
    void 마감이_지나면_기다리지_않고_실패로_돌아간다() throws Exception {
        ServerGate gate = new ServerGate(1);
        assertThat(gate.enter(GenerationKind.LIGHT, in(Duration.ofSeconds(5)))).isTrue();

        // 줄에서 기다리다 마감이 지나면 false, 줄에서도 빠진다
        long start = System.nanoTime();
        assertThat(gate.enter(GenerationKind.LIGHT, in(Duration.ofMillis(200)))).isFalse();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
        assertThat(gate.waitingCount()).isZero();

        // 이미 지난 마감이면 바로 false
        start = System.nanoTime();
        assertThat(gate.enter(GenerationKind.LIGHT, Instant.now().minusSeconds(1))).isFalse();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(100));

        // 실패한 요청은 자리를 차지하지 않는다
        gate.leave();
        assertThat(gate.runningCount()).isZero();
        assertThat(gate.enter(GenerationKind.SECTION, in(Duration.ofSeconds(1)))).isTrue();
        gate.leave();
    }
}
