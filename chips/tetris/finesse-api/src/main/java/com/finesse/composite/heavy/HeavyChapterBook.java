package com.finesse.composite.heavy;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/**
 * heavy 챕터 사전 — {@code chips/tetris/config/heavy-chapters.json}을 읽은 것.
 * 문구는 모놀리식 llm-server {@code app/prompt.py}의 HEAVY_CHAPTERS·HEAVY_LABELS·HEAVY_RANGES에서 옮겼다 (조각 9 지침 2.1).
 */
public record HeavyChapterBook(
        String version,
        Context context,
        @JsonProperty("compare_phrases") List<String> comparePhrases,
        @JsonProperty("compare_rule") String compareRule,
        @JsonProperty("common_must_not") List<String> commonMustNot,
        @JsonProperty("ranges_version") String rangesVersion,
        Map<String, List<Double>> ranges,
        List<Chapter> chapters) {

    public record Context(String name, List<String> rules) {
    }

    /**
     * @param compare 상대와 비교한 값(delta_*) 챕터면 true — description 끝에 비교 기준 줄을 붙인다
     * @param engine  엔진을 부르는 챕터면 true(기본). rivals만 false — 칩이 직접 문장을 만든다
     */
    public record Chapter(
            String key,
            String title,
            boolean compare,
            Boolean engine,
            List<String> description,
            @JsonProperty("must_not") List<String> mustNot,
            List<ChapterItem> items) {

        public boolean callsEngine() {
            return engine == null || engine;
        }
    }

    public record ChapterItem(String key, String label, String meaning) {
    }

    public Chapter chapter(String key) {
        return chapters.stream()
                .filter(c -> c.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("heavy 챕터 사전에 없는 챕터: " + key));
    }

    public static HeavyChapterBook load(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return JsonMapper.builder().build().readValue(in, HeavyChapterBook.class);
        } catch (IOException e) {
            throw new UncheckedIOException("heavy 챕터 사전을 읽지 못했습니다: " + file, e);
        }
    }
}
