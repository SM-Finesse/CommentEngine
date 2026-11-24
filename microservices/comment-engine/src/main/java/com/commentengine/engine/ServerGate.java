package com.commentengine.engine;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 생성 서버 한 대 앞의 문 — 한 번에 정해진 수(기본 1)만 들여보내고 나머지는 줄을 세운다.
 * <ul>
 *   <li>자리가 없으면 줄에서 기다린다</li>
 *   <li>줄 순서: {@link GenerationKind#LIGHT}가 {@link GenerationKind#SECTION}보다 먼저. 같은 종류끼리는 온 순서대로</li>
 *   <li>호출하는 쪽이 정한 마감까지 차례가 오지 않으면 들어가지 않고 실패(false)로 돌아간다.
 *       마감이 이미 지났으면 자리가 비어 있어도 들어가지 않는다</li>
 *   <li>나올 때 줄에 다음 사람이 있으면 자리를 그대로 넘긴다 (빈자리를 새로 온 요청이 가로채지 못한다)</li>
 * </ul>
 * 왜 필요한가: 생성 서버는 요청을 하나씩 처리한다. 동시에 여러 개를 보내면 뒤의 요청은 서버 안에서 기다리다
 * 호출 타임아웃에 걸린다. 여기서 줄을 세우면 타임아웃은 실제로 보낸 때부터 센다.
 */
final class ServerGate {

    private static final class Waiter {
        final GenerationKind kind;
        final long order;
        final Condition turn;
        boolean granted;

        Waiter(GenerationKind kind, long order, Condition turn) {
            this.kind = kind;
            this.order = order;
            this.turn = turn;
        }
    }

    private final ReentrantLock lock = new ReentrantLock();
    private final PriorityQueue<Waiter> waiting = new PriorityQueue<>(
            Comparator.<Waiter>comparingInt(w -> w.kind.ordinal()).thenComparingLong(w -> w.order));
    private final int permits;
    private int running;
    private long nextOrder;

    /** @param permits 한 번에 들어가는 수 (설정 {@code engine.gate.permits}). 1보다 작으면 1 */
    ServerGate(int permits) {
        this.permits = Math.max(1, permits);
    }

    /**
     * 들어간다. 자리가 없으면 deadline까지 줄에서 기다린다.
     *
     * @return 들어갔으면 true (끝나면 반드시 {@link #leave()}), 마감까지 차례가 오지 않았으면 false
     * @throws InterruptedException 기다리는 중 끊기면. 이때는 들어가지 않은 것이다
     */
    boolean enter(GenerationKind kind, Instant deadline) throws InterruptedException {
        long nanos = Duration.between(Instant.now(), deadline).toNanos();
        if (nanos <= 0) {
            return false;
        }
        lock.lock();
        try {
            if (running < permits && waiting.isEmpty()) {
                running++;
                return true;
            }
            Waiter me = new Waiter(kind, nextOrder++, lock.newCondition());
            waiting.add(me);
            try {
                while (!me.granted) {
                    if (nanos <= 0) {
                        waiting.remove(me);
                        return false;
                    }
                    nanos = me.turn.awaitNanos(nanos);
                }
                return true;
            } catch (InterruptedException e) {
                if (me.granted) {
                    passOn(); // 차례를 받은 직후 끊겼다 — 받은 자리를 다음 사람에게 넘긴다
                } else {
                    waiting.remove(me);
                }
                throw e;
            }
        } finally {
            lock.unlock();
        }
    }

    /** 나온다. 줄에 사람이 있으면 자리를 그대로 넘기고, 없으면 자리를 비운다 */
    void leave() {
        lock.lock();
        try {
            passOn();
        } finally {
            lock.unlock();
        }
    }

    private void passOn() {
        Waiter next = waiting.poll();
        if (next != null) {
            next.granted = true; // running 수는 그대로 — 자리를 넘긴 것
            next.turn.signal();
        } else {
            running--;
        }
    }

    /** 줄에서 기다리는 수 (로그 · 시험용) */
    int waitingCount() {
        lock.lock();
        try {
            return waiting.size();
        } finally {
            lock.unlock();
        }
    }

    /** 지금 안에 들어가 있는 수 (로그 · 시험용) */
    int runningCount() {
        lock.lock();
        try {
            return running;
        } finally {
            lock.unlock();
        }
    }
}
