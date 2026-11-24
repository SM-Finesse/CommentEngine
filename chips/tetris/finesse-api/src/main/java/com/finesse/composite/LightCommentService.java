package com.finesse.composite;

import com.commentengine.api.ItemSentence;
import com.commentengine.api.LightRequest;
import com.commentengine.api.LightResponse;
import com.finesse.composite.dto.LightCommentResponse;
import com.finesse.composite.light.LightCommentTemplates;
import com.finesse.composite.light.LightItems;
import com.finesse.composite.light.LightMetrics;
import com.finesse.composite.light.LightRequestBuilder;
import com.finesse.tetris.api.StatsResponse;
import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * light 코멘트 흐름 (설계서 7장 light, 조각 8 지침서 3.3).
 * <ol>
 *   <li>통계를 받는다 (조각 4 {@link AnalyticsHttp}). 200이 아니면 통계 경로와 똑같이 그대로 넘긴다</li>
 *   <li>통계가 끝난 시각부터 light 마감 40초</li>
 *   <li>캐시(열쇠 = username@updated_at)에 없으면 만든다: 콜드스타트·고른 지표 0개는 엔진을 부르지 않고, 그 밖은 엔진에</li>
 *   <li>엔진이 못 만든 항목·요약은 칩의 대체 문장으로 채운다</li>
 * </ol>
 */
@Service
public class LightCommentService {

    private static final Logger log = LoggerFactory.getLogger(LightCommentService.class);

    /** 모놀리식 CommentService.computeLight와 같은 문장 */
    static final String COLD_START_SUMMARY = "최근 매치 데이터가 부족해 하이라이트를 표시할 수 없습니다.";
    static final String NO_HIGHLIGHT_SUMMARY = "이번 전적에서는 AI 코멘트로 짚을 지표를 고르지 못했습니다.";

    /** 캐시 값 — cacheable은 응답에 넣지 않는 내부 표시 */
    record Computed(LightCommentResponse response, boolean cacheable) {
    }

    private final AnalyticsHttp analytics;
    private final EngineClient engine;
    private final LightRequestBuilder builder;
    private final LightCommentTemplates templates;
    private final LightMetrics metrics;
    private final long deadlineMs;
    private final int analyticsCircuitOpenSeconds;
    private final AsyncCache<String, Computed> cache;
    private final JsonMapper statsMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public LightCommentService(AnalyticsHttp analytics, EngineClient engine, LightRequestBuilder builder,
                               LightCommentTemplates templates, LightMetrics metrics,
                               @Value("${light.deadline-seconds}") long deadlineSeconds,
                               @Value("${light.cache.ttl}") Duration ttl,
                               @Value("${light.cache.max-size}") long maxSize,
                               @Value("${resilience4j.circuitbreaker.instances.analytics.wait-duration-in-open-state}")
                               Duration analyticsCircuitOpen) {
        this.analytics = analytics;
        this.engine = engine;
        this.builder = builder;
        this.templates = templates;
        this.metrics = metrics;
        this.deadlineMs = deadlineSeconds * 1000;
        this.analyticsCircuitOpenSeconds = (int) Math.max(1, analyticsCircuitOpen.toSeconds());
        // 같은 열쇠의 동시 요청은 한 번만 만든다. 엔진 호출(최대 40초)은 가상 스레드에서
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(maxSize)
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .buildAsync();
    }

    /** @param rawUsername 화면이 보낸 그대로(인코딩된) 유저명 — analytics에 다시 인코딩하지 않고 넘긴다 */
    public ResponseEntity<?> light(String rawUsername) {
        AnalyticsHttp.Relayed relayed = fetchStats(rawUsername);
        if (relayed.status() != 200) {
            return relay(relayed); // 404 USER_NOT_FOUND 등 — 통계 경로와 같은 응답
        }
        long statsDone = System.nanoTime();
        StatsResponse stats = statsMapper.readValue(relayed.body(), StatsResponse.class);
        String key = stats.username() + "@" + stats.updatedAt();

        CompletableFuture<Computed> future = cache.get(key, (k, executor) ->
                CompletableFuture.supplyAsync(() -> compute(stats, statsDone), executor));
        Computed computed;
        try {
            computed = future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re; // EngineCallException 등 — ApiErrorHandler가 화면 코드로 바꾼다 (실패한 값은 캐시에 남지 않음)
            }
            throw e;
        }
        if (!computed.cacheable()) {
            cache.synchronous().invalidate(key); // 보여 주되 남기지 않는다 — 다음 요청 때 다시 만든다
        }
        return ResponseEntity.ok(computed.response());
    }

    /** 시험용 — 캐시 비우기 */
    void clearCache() {
        cache.synchronous().invalidateAll();
    }

    private Computed compute(StatsResponse stats, long statsDone) {
        if (stats.coldStart()) {
            return new Computed(new LightCommentResponse(COLD_START_SUMMARY, List.of()), true);
        }
        List<LightItems.Pick> picks = LightItems.from(stats, metrics);
        if (picks.isEmpty()) {
            // 엔진을 부르지 않는 응답이라 남기지 않아도 비용이 없다 (하이라이트 3개 미만이라 남기지 않음)
            return new Computed(new LightCommentResponse(NO_HIGHLIGHT_SUMMARY, List.of()), false);
        }
        Random rng = ThreadLocalRandom.current();
        long remaining = deadlineMs - (System.nanoTime() - statsDone) / 1_000_000;
        LightRequest request = builder.build(stats, picks, (int) Math.max(0, remaining), rng);
        LightResponse result = engine.light(request);
        log.info("light 엔진 응답: 항목 {}개 중 failed {}, checks {}, versions {}",
                picks.size(), result.failed(), result.checks(), result.versions());
        return assemble(picks, result, rng);
    }

    /** 엔진 응답 → 화면 응답. 고른 순서대로 key로 짝을 짓고(엔진 순서를 믿지 않음), 빈 자리는 대체 문장 */
    Computed assemble(List<LightItems.Pick> picks, LightResponse result, Random rng) {
        Map<String, ItemSentence> byKey = new HashMap<>();
        int maxAttempts = 0;
        if (result.items() != null) {
            for (ItemSentence s : result.items()) {
                if (s != null && s.key() != null) {
                    byKey.putIfAbsent(s.key(), s);
                    maxAttempts = Math.max(maxAttempts, s.attempts());
                }
            }
        }
        Set<String> failed = result.failed() == null ? Set.of() : new HashSet<>(result.failed());

        List<LightCommentResponse.Highlight> highlights = new ArrayList<>();
        int fallbacks = 0;
        for (LightItems.Pick p : picks) {
            ItemSentence s = byKey.get(p.key());
            if (failed.contains(p.key()) || s == null || s.sentence() == null || s.sentence().isBlank()) {
                highlights.add(new LightCommentResponse.Highlight(p.key(), templates.sentence(p.key(), p.value(), p.zone(), rng)));
                fallbacks++;
            } else {
                highlights.add(new LightCommentResponse.Highlight(p.key(), s.sentence()));
            }
        }
        String summary = result.summary();
        if (summary == null || summary.isBlank()) {
            summary = templates.summary(picks.stream().map(LightItems.Pick::key).toList(),
                    picks.stream().map(LightItems.Pick::value).toList(),
                    picks.stream().map(LightItems.Pick::zone).toList(), rng);
            fallbacks++;
        }
        if (fallbacks > 0) {
            log.info("light: 대체 문장 {}개가 섞여 캐시하지 않음", fallbacks);
        }
        boolean cacheable = fallbacks == 0 && highlights.size() == LightItems.MAX;
        return new Computed(new LightCommentResponse(summary, List.copyOf(highlights), maxAttempts), cacheable);
    }

    private AnalyticsHttp.Relayed fetchStats(String rawUsername) {
        try {
            return analytics.get("/api/v1/stats/" + rawUsername);
        } catch (AnalyticsUnreachableException | AnalyticsTimeoutException e) {
            log.warn("light: 통계를 받지 못함: {}", e.getMessage());
            throw new StatsUnavailableException(e.getMessage(), StatsRelayController.UNREACHABLE_RETRY_AFTER_SECONDS, e);
        } catch (CallNotPermittedException e) {
            throw new StatsUnavailableException("tetris-analytics 회로 열림", analyticsCircuitOpenSeconds, e);
        }
    }

    private static ResponseEntity<byte[]> relay(AnalyticsHttp.Relayed r) {
        ResponseEntity.BodyBuilder out = ResponseEntity.status(r.status());
        if (r.contentType() != null) {
            out.contentType(r.contentType());
        }
        if (r.retryAfter() != null) {
            out.header(HttpHeaders.RETRY_AFTER, r.retryAfter());
        }
        return out.body(r.body());
    }
}
