package com.finesse.composite;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * light 흐름 GET /api/v1/comment/{username}?scope=light — 가짜 analytics + 가짜 엔진 (지침서 3.6의 1~10, 14).
 * 엔진 장애(닿지 않음·회로)는 {@link EngineResilienceTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "resilience4j.retry.instances.engine.wait-duration=100ms",
        "resilience4j.retry.instances.analytics.wait-duration=100ms"})
class LightCommentFlowTest {

    static final HttpServer ANALYTICS = FakeServers.analytics();
    static final FakeServers.Engine ENGINE = new FakeServers.Engine();

    static {
        ENGINE.start(0);
    }

    @DynamicPropertySource
    static void urls(DynamicPropertyRegistry r) {
        r.add("analytics.base-url", () -> "http://127.0.0.1:" + ANALYTICS.getAddress().getPort());
        r.add("engine.base-url", () -> "http://127.0.0.1:" + ENGINE.port());
    }

    @AfterAll
    static void stop() {
        ANALYTICS.stop(0);
        ENGINE.stop();
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    LightCommentService service;

    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void reset() {
        ENGINE.reset();
        service.clearCache();
        FakeServers.updatedAt = "2026-11-24T09:00:00Z";
    }

    @Test
    void 정상이면_모놀리식과_같은_모양_고른_순서대로_attempt_count는_최댓값() throws Exception {
        HttpResponse<String> resp = get("/api/v1/comment/abc?scope=light");

        assertThat(resp.statusCode()).isEqualTo(200);
        JsonNode body = FakeServers.JSON.readTree(resp.body());
        assertThat(fieldNames(body)).containsExactly("light_summary", "highlights", "attempt_count");
        assertThat(body.path("light_summary").asString()).isEqualTo("엔진 요약입니다.");
        // 가짜 엔진은 items를 뒤집어 보내지만 화면에는 picks 순서대로
        assertThat(stats(body)).containsExactly("delta_comeback", "delta_app", "session_vs_slope");
        assertThat(body.path("highlights").get(0).path("sentence").asString()).isEqualTo("엔진 문장 delta_comeback.");
        assertThat(fieldNames(body.path("highlights").get(0))).containsExactly("stat", "sentence");
        assertThat(body.path("attempt_count").asInt()).isEqualTo(2);
    }

    @Test
    void 엔진이_받은_몸통은_항목_3개_남은_마감_context가_있고_식별자가_없다() throws Exception {
        get("/api/v1/comment/secretuser?scope=light");

        JsonNode sent = FakeServers.JSON.readTree(ENGINE.lastBody);
        assertThat(sent.path("items")).hasSize(3);
        assertThat(sent.path("deadline_ms").asInt()).isBetween(1000, 40000);
        assertThat(sent.path("context").path("name").asString()).isEqualTo("TETR.IO 전적 분석 페이지 첫 화면");
        assertThat(sent.path("items").get(1).path("reliability").asString()).isEqualTo("insufficient"); // delta_app low_sample
        assertThat(ENGINE.lastBody).doesNotContain("secretuser").doesNotContain("updated_at")
                .doesNotContain("2026-11-24").doesNotContain("username");
    }

    @Test
    void 콜드스타트는_엔진을_부르지_않고_정해진_문장_attempt_count_칸이_없다() throws Exception {
        HttpResponse<String> resp = get("/api/v1/comment/cold?scope=light");

        assertThat(resp.statusCode()).isEqualTo(200);
        JsonNode body = FakeServers.JSON.readTree(resp.body());
        assertThat(body.path("light_summary").asString()).isEqualTo("최근 매치 데이터가 부족해 하이라이트를 표시할 수 없습니다.");
        assertThat(body.path("highlights")).isEmpty();
        assertThat(body.has("attempt_count")).isFalse();
        assertThat(ENGINE.hits.get()).isZero();
    }

    @Test
    void 고른_지표가_0개면_엔진을_부르지_않고_정해진_문장() throws Exception {
        HttpResponse<String> resp = get("/api/v1/comment/nopick?scope=light");

        assertThat(resp.statusCode()).isEqualTo(200);
        JsonNode body = FakeServers.JSON.readTree(resp.body());
        assertThat(body.path("light_summary").asString()).isEqualTo("이번 전적에서는 AI 코멘트로 짚을 지표를 고르지 못했습니다.");
        assertThat(body.path("highlights")).isEmpty();
        assertThat(body.has("attempt_count")).isFalse();
        assertThat(ENGINE.hits.get()).isZero();
    }

    @Test
    void 통계가_404면_통계_경로와_같은_응답이고_엔진을_부르지_않는다() throws Exception {
        HttpResponse<String> light = get("/api/v1/comment/nosuch?scope=light");
        HttpResponse<String> stats = get("/api/v1/stats/nosuch");

        assertThat(light.statusCode()).isEqualTo(404).isEqualTo(stats.statusCode());
        assertThat(light.body()).isEqualTo(FakeServers.NOT_FOUND_JSON).isEqualTo(stats.body());
        assertThat(ENGINE.hits.get()).isZero();
    }

    @Test
    void failed에_든_항목만_대체_문장으로_채운다() throws Exception {
        ENGINE.mode = FakeServers.EngineMode.FAIL_APP;

        JsonNode body = FakeServers.JSON.readTree(get("/api/v1/comment/abc?scope=light").body());

        assertThat(stats(body)).containsExactly("delta_comeback", "delta_app", "session_vs_slope");
        assertThat(body.path("highlights").get(0).path("sentence").asString()).isEqualTo("엔진 문장 delta_comeback.");
        assertThat(body.path("highlights").get(1).path("sentence").asString()).endsWith("블록당 공격력에서 앞서는 편입니다(+0.12).");
        assertThat(body.path("highlights").get(2).path("sentence").asString()).isEqualTo("엔진 문장 session_vs_slope.");
    }

    @Test
    void 대체_문장이_섞이면_남기지_않고_없으면_남기며_updated_at이_바뀌면_다시_만든다() throws Exception {
        ENGINE.mode = FakeServers.EngineMode.FAIL_APP;
        get("/api/v1/comment/abc?scope=light");
        get("/api/v1/comment/abc?scope=light");
        assertThat(ENGINE.hits.get()).as("대체 문장이 섞인 응답은 남기지 않음").isEqualTo(2);

        ENGINE.reset();
        get("/api/v1/comment/abc?scope=light");
        get("/api/v1/comment/abc?scope=light");
        assertThat(ENGINE.hits.get()).as("대체 문장 없는 응답은 두 번째에 엔진 0회").isEqualTo(1);

        FakeServers.updatedAt = "2026-11-24T09:30:00Z"; // 통계가 새로 계산됨
        get("/api/v1/comment/abc?scope=light");
        assertThat(ENGINE.hits.get()).as("열쇠가 바뀌어 다시 만듦").isEqualTo(2);
    }

    @Test
    void 요약이_빈_문자열이면_대체_요약이고_남기지_않는다() throws Exception {
        ENGINE.mode = FakeServers.EngineMode.EMPTY_SUMMARY;

        JsonNode body = FakeServers.JSON.readTree(get("/api/v1/comment/abc?scope=light").body());
        get("/api/v1/comment/abc?scope=light");

        assertThat(body.path("light_summary").asString())
                .satisfiesAnyOf(s -> assertThat(s).startsWith("이번 분석에서 눈에 띄는 점은"),
                        s -> assertThat(s).startsWith("대전 기록에서 두드러지는 부분은"));
        assertThat(body.path("highlights").get(0).path("sentence").asString()).isEqualTo("엔진 문장 delta_comeback.");
        assertThat(ENGINE.hits.get()).as("남기지 않음").isEqualTo(2);
    }

    @Test
    void 엔진_503은_Retry_After를_그대로_넘기고_다시_부르지_않는다() throws Exception {
        ENGINE.error(503, "LLM_UNAVAILABLE", "30");

        HttpResponse<String> resp = get("/api/v1/comment/abc?scope=light");

        assertThat(resp.statusCode()).isEqualTo(503);
        assertThat(resp.body()).isEqualTo(
                "{\"error_code\":\"LLM_UNAVAILABLE\",\"message\":\"일시적으로 코멘트를 생성할 수 없습니다, 잠시 후 다시 시도\"}");
        assertThat(resp.headers().firstValue("Retry-After")).hasValue("30");
        assertThat(ENGINE.hits.get()).isEqualTo(1);
    }

    @Test
    void 엔진_502_504_400은_모놀리식_코드로_바꾸고_각각_한_번만_부른다() throws Exception {
        Object[][] cases = {
                {502, "LLM_FORMAT", 502, "{\"error_code\":\"LLM_FORMAT_ERROR\",\"message\":\"AI 코멘트를 만들지 못했습니다, 잠시 후 다시 시도\"}"},
                {504, "LLM_TIMEOUT", 504, "{\"error_code\":\"LLM_TIMEOUT\",\"message\":\"AI 코멘트 생성 시간이 초과되었습니다, 잠시 후 다시 시도\"}"},
                {400, "INVALID_PAYLOAD", 500, "{\"error_code\":\"INTERNAL_ERROR\",\"message\":\"일시적인 오류가 발생했습니다, 잠시 후 다시 시도\"}"},
                {500, "SOMETHING_NEW", 502, "{\"error_code\":\"LLM_FORMAT_ERROR\",\"message\":\"AI 코멘트를 만들지 못했습니다, 잠시 후 다시 시도\"}"},
        };
        for (Object[] c : cases) {
            ENGINE.reset();
            service.clearCache();
            ENGINE.error((int) c[0], (String) c[1], null);

            HttpResponse<String> resp = get("/api/v1/comment/abc?scope=light");

            assertThat(resp.statusCode()).as(c[1].toString()).isEqualTo((int) c[2]);
            assertThat(resp.body()).as(c[1].toString()).isEqualTo(c[3]);
            assertThat(ENGINE.hits.get()).as(c[1] + " 엔진 받은 횟수").isEqualTo(1);
        }
    }

    @Test
    void scope는_heavy_501_틀린_값_400_없으면_400() throws Exception {
        HttpResponse<String> heavy = get("/api/v1/comment/abc?scope=heavy");
        assertThat(heavy.statusCode()).isEqualTo(501);
        assertThat(heavy.body()).isEqualTo("{\"error_code\":\"NOT_IMPLEMENTED\",\"message\":\"heavy는 아직 준비 중입니다\"}");

        HttpResponse<String> bad = get("/api/v1/comment/abc?scope=abc");
        assertThat(bad.statusCode()).isEqualTo(400);
        assertThat(bad.body()).isEqualTo("{\"error_code\":\"BAD_REQUEST\",\"message\":\"scope는 light 또는 heavy만 허용됩니다: abc\"}");

        HttpResponse<String> none = get("/api/v1/comment/abc");
        assertThat(none.statusCode()).isEqualTo(400);
        assertThat(none.body()).isEqualTo("{\"error_code\":\"BAD_REQUEST\",\"message\":\"필수 파라미터가 없습니다: scope\"}");
        assertThat(ENGINE.hits.get()).isZero();
    }

    private static List<String> stats(JsonNode body) {
        List<String> out = new ArrayList<>();
        body.path("highlights").forEach(h -> out.add(h.path("stat").asString()));
        return out;
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> out = new ArrayList<>();
        node.propertyNames().forEach(out::add);
        return out;
    }

    private HttpResponse<String> get(String pathAndQuery) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + pathAndQuery)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
