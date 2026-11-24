package com.finesse.composite.light;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * light 지표 사전 {@code chips/tetris/config/light-metrics.json} (설계서 7장 "칩의 지표 사전(칩 안 JSON)").
 * 지표마다 화면 이름·뜻·묶음·비교 여부·값 자릿수, 그리고 엔진 요청에 넣는 공통 규칙·금지 낱말을 담는다.
 * 코드가 아니라 칩 설정이라 config 폴더에 두고, 다시 빌드하지 않고 문구를 고칠 수 있다. 시작할 때 한 번 읽는다.
 *
 * @param version        사전 버전 (로그용)
 * @param context        엔진 요청의 context (무대 이름·공통 규칙)
 * @param comparePhrases 상대와 비교하는 지표 앞에 붙이는 비교 표현 5개
 * @param commonMustNot  모든 항목의 금지 낱말
 * @param metrics        지표 키 → 지표
 */
public record LightMetrics(
        String version,
        Context context,
        @JsonProperty("compare_phrases") List<String> comparePhrases,
        @JsonProperty("common_must_not") List<String> commonMustNot,
        Map<String, Metric> metrics) {

    public record Context(String name, List<String> rules) {
    }

    /**
     * @param label    화면·문장에 쓰는 지표 이름 (모놀리식 llm-server names.py COMMENT_NAMES)
     * @param group    방향 문장·규칙 묶음: rank·vs_apm·tendency·cheese·strength_split·comeback·session
     * @param compare  상대와 비교하는 지표인가 — 대체 문장 앞에 비교 표현을 붙이고, 비교 기준 규칙을 보낸다
     * @param decimals 값 표기 소수 자릿수
     * @param meaning  지표 뜻 (영어·숫자 없음 — 엔진 검사 CHARSET·NUMBER에 걸리지 않게)
     */
    public record Metric(String label, String group, boolean compare, int decimals, String meaning) {
    }

    /** 이 사전에 있는 지표인가 */
    public boolean has(String key) {
        return metrics.containsKey(key);
    }

    public Metric metric(String key) {
        Metric m = metrics.get(key);
        if (m == null) {
            throw new IllegalArgumentException("지표 사전에 없는 지표: " + key);
        }
        return m;
    }

    /** 파일에서 읽는다. 빠진 칸이 있으면 시작할 때 실패한다 */
    public static LightMetrics load(Path file) {
        try {
            LightMetrics m = JsonMapper.builder()
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .build()
                    .readValue(Files.readString(file), LightMetrics.class);
            m.check();
            return m;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("light 지표 사전을 읽지 못함: " + file, e);
        }
    }

    private void check() {
        if (context == null || context.name() == null || comparePhrases == null || comparePhrases.isEmpty()
                || commonMustNot == null || metrics == null || metrics.isEmpty()) {
            throw new IllegalStateException("light 지표 사전에 빠진 칸이 있습니다 (context·compare_phrases·common_must_not·metrics)");
        }
        metrics.forEach((key, m) -> {
            if (m.label() == null || m.group() == null || m.meaning() == null) {
                throw new IllegalStateException("light 지표 사전 " + key + "에 label·group·meaning이 있어야 합니다");
            }
            if (!LightCommentTemplates.GROUPS.contains(m.group())) {
                throw new IllegalStateException("light 지표 사전 " + key + "의 group을 모름: " + m.group());
            }
        });
    }
}
