package com.commentengine.engine.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commentengine.api.Degree;
import com.commentengine.api.Item;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SentenceCheckerTest {

    private static final Map<Degree, List<String>> DEGREE_FORBIDDEN = Map.of(
            Degree.STRONG, List.of("비슷", "같은 수준", "하는 편"),
            Degree.WEAK, List.of("뚜렷", "크게", "압도"),
            Degree.MID, List.of("뚜렷", "크게", "같은 수준"),
            Degree.ZERO, List.of("뚜렷", "크게", "높", "낮"));

    private static final List<String> LEAK_MARKERS = List.of("규칙:", "방향:", "{{", "value_text");

    private static final String GOOD = "평균 점수가 높은 편입니다 (+12.3%)";

    private final SentenceChecker checker =
            new SentenceChecker(new CheckSettings(10, 40, DEGREE_FORBIDDEN, LEAK_MARKERS));

    private static Item item(String label, String valueText, Degree degree, List<String> mustNot) {
        return new Item("k1", label, "뜻", valueText, "방향 문장", degree, "sufficient", List.of(), mustNot);
    }

    private static Item item(String label, String valueText) {
        return item(label, valueText, Degree.MID, List.of());
    }

    private static Item item() {
        return item("평균 점수", "+12.3%");
    }

    private List<CheckCode> failed(String text, Item item, TemplateKind kind) {
        return checker.check(text, item, kind).failedCodes();
    }

    private List<CheckCode> failed(String text, Item item) {
        return failed(text, item, TemplateKind.SENTENCE);
    }

    // ---- NUMBER

    @Test
    void 괄호_안이_value_text와_같으면_통과한다() {
        assertThat(failed(GOOD, item())).isEmpty();
        assertThat(failed("평균 점수가 높은 편입니다 ( +12.3% )", item())).isEmpty();
    }

    @Test
    void 값을_안_쓴_문장도_통과한다() {
        assertThat(failed("평균 점수가 높은 편입니다", item())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "평균 점수가 5번 높은 편입니다",
            "평균 점수가 12.3% 높은 편입니다 (+12.3%)",
            "평균 점수가 +12.3% 높은 편입니다"})
    void 괄호_밖에_숫자가_있으면_실패한다(String text) {
        assertThat(failed(text, item())).containsExactly(CheckCode.NUMBER);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "평균 점수가 높은 편입니다 (12.3%)",
            "평균 점수가 높은 편입니다 (-12.3%)",
            "평균 점수가 높은 편입니다 (+12.30%)",
            "평균 점수가 높은 편입니다 (값 +12.3%)",
            "평균 점수가 높은 편입니다 (최근 기록)",
            "평균 점수가 높은 편입니다 ()"})
    void 괄호_안이_value_text와_다르면_실패한다(String text) {
        assertThat(failed(text, item())).containsExactly(CheckCode.NUMBER);
    }

    @Test
    void 괄호가_두_개면_둘_다_같아야_통과한다() {
        assertThat(failed("평균 점수(+12.3%)가 높은 편입니다 (+12.3%)", item())).isEmpty();
        assertThat(failed("평균 점수(+12.3%)가 높은 편입니다 (9.9%)", item()))
                .containsExactly(CheckCode.NUMBER);
        assertThat(failed("평균 점수(9.9%)가 높은 편입니다 (+12.3%)", item()))
                .containsExactly(CheckCode.NUMBER);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "평균 점수가 높은 편입니다 (+12.3%",
            "평균 점수가 높은 편입니다 +12.3%)",
            "평균 점수가 높은 편입니다 (",
            "평균 점수가 높은 편입니다 ((+12.3%))",
            "평균 점수가 높은 편입니다 (+12.3% (+12.3%))"})
    void 괄호_짝이_안_맞거나_겹치면_실패한다(String text) {
        assertThat(failed(text, item())).containsExactly(CheckCode.NUMBER);
    }

    @Test
    void summary에_숫자가_있으면_실패한다() {
        assertThat(failed(GOOD, item(), TemplateKind.SUMMARY)).containsExactly(CheckCode.NUMBER);
        assertThat(failed("평균 점수가 높은 편입니다", item(), TemplateKind.SUMMARY)).isEmpty();
    }

    @Test
    void section은_sentence와_같이_값_인용을_허용한다() {
        assertThat(failed(GOOD, item(), TemplateKind.SECTION)).isEmpty();
        assertThat(failed("평균 점수가 5번 높은 편입니다", item(), TemplateKind.SECTION))
                .containsExactly(CheckCode.NUMBER);
    }

    @Test
    void label_안의_숫자는_셈하지_않는다() {
        Item item = item("40줄 기록", "+1.5");
        assertThat(failed("40줄 기록이 빠른 편입니다 (+1.5)", item)).isEmpty();
        assertThat(failed("40줄 기록이 빠른 편입니다", item, TemplateKind.SUMMARY)).isEmpty();
        assertThat(failed("40줄 기록이 40번 빠른 편입니다 (+1.5)", item))
                .containsExactly(CheckCode.NUMBER);
    }

    @Test
    void label에_괄호가_있어도_통과한다() {
        assertThat(failed("응답 속도(상위 10)가 빠른 편입니다 (12초)", item("응답 속도(상위 10)", "12초")))
                .isEmpty();
    }

    @Test
    void label이_value_text_안에_들어_있어도_인용이_깨지지_않는다() {
        assertThat(failed("점이 전보다 많은 편입니다 (12점)", item("점", "12점"))).isEmpty();
    }

    @Test
    void value_text의_정규식_기호와_괄호는_글자_그대로_본다() {
        assertThat(failed("평균 점수가 높은 편입니다 (12 (상위 3%))", item("평균 점수", "12 (상위 3%)")))
                .isEmpty();
        assertThat(failed("평균 점수가 높은 편입니다 (1,5)", item("평균 점수", "1.5")))
                .containsExactly(CheckCode.NUMBER);
    }

    // ---- DEGREE (강도 4칸)

    @ParameterizedTest
    @CsvSource({
            "STRONG, 평균 점수가 뚜렷하게 높습니다",
            "WEAK, 평균 점수가 조금 높은 편입니다",
            "MID, 평균 점수가 높은 편에 속합니다",
            "ZERO, 평균 점수가 이전과 같은 수준입니다"})
    void 강도에_맞는_표현은_통과한다(Degree degree, String text) {
        assertThat(failed(text, item("평균 점수", "+12.3%", degree, List.of()))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "STRONG, 평균 점수가 이전과 비슷합니다",
            "STRONG, 평균 점수가 이전과 같은 수준입니다",
            "STRONG, 평균 점수가 상승하는 편입니다",
            "WEAK, 평균 점수가 뚜렷하게 올랐습니다",
            "WEAK, 평균 점수가 크게 올랐습니다",
            "WEAK, 평균 점수가 압도적인 흐름입니다",
            "MID, 평균 점수가 뚜렷하게 올랐습니다",
            "MID, 평균 점수가 크게 올랐습니다",
            "MID, 평균 점수가 이전과 같은 수준입니다",
            "ZERO, 평균 점수가 뚜렷하게 올랐습니다",
            "ZERO, 평균 점수가 크게 올랐습니다",
            "ZERO, 평균 점수가 조금 높습니다",
            "ZERO, 평균 점수가 조금 낮습니다"})
    void 강도에_안_맞는_표현은_실패한다(Degree degree, String text) {
        assertThat(failed(text, item("평균 점수", "+12.3%", degree, List.of())))
                .containsExactly(CheckCode.DEGREE);
    }

    @Test
    void 금지_표현을_정해_두지_않은_degree는_강도_검사를_하지_않는다() {
        SentenceChecker noDegree = new SentenceChecker(new CheckSettings(10, 40, Map.of(), LEAK_MARKERS));
        assertThat(noDegree.check("평균 점수가 크게 올랐습니다", item(), TemplateKind.SUMMARY).passed()).isTrue();
    }

    // ---- MUST_NOT

    @Test
    void 금지_낱말이_없으면_통과하고_부분으로라도_있으면_실패한다() {
        Item item = item("평균 점수", "+12.3%", Degree.MID, List.of("초보"));
        assertThat(failed(GOOD, item)).isEmpty();
        assertThat(failed("평균 점수가 초보자보다 많은 편입니다", item)).containsExactly(CheckCode.MUST_NOT);
    }

    @Test
    void must_not이_없거나_빈_낱말이면_통과한다() {
        assertThat(failed(GOOD, item("평균 점수", "+12.3%", Degree.MID, null))).isEmpty();
        assertThat(failed(GOOD, item("평균 점수", "+12.3%", Degree.MID, List.of("", " ")))).isEmpty();
    }

    // ---- FORMAT

    @Test
    void 길이는_양_끝을_포함한다() {
        assertThat(failed("가".repeat(10), item())).isEmpty();
        assertThat(failed("가".repeat(40), item())).isEmpty();
        assertThat(failed("가".repeat(9), item())).containsExactly(CheckCode.FORMAT);
        assertThat(failed("가".repeat(41), item())).containsExactly(CheckCode.FORMAT);
    }

    @Test
    void 길이는_공백을_포함해_코드포인트로_센다() {
        assertThat(failed("가".repeat(4) + " " + "가".repeat(5), item())).isEmpty();
        // 그림 문자 하나는 char 2개지만 글자 1개다
        String emoji = Character.toString(0x1F600);
        assertThat(failed(emoji.repeat(40), item())).isEmpty();
        assertThat(failed(emoji.repeat(9), item())).containsExactly(CheckCode.FORMAT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\n", "\r\n", "\r"})
    void 줄바꿈이_있으면_실패한다(String lineBreak) {
        assertThat(failed("평균 점수가" + lineBreak + "높은 편입니다", item())).containsExactly(CheckCode.FORMAT);
    }

    @Test
    void 빈_문장은_실패한다() {
        assertThat(failed(null, item())).containsExactly(CheckCode.FORMAT);
        assertThat(failed("", item())).containsExactly(CheckCode.FORMAT);
        assertThat(failed(" ".repeat(20), item())).containsExactly(CheckCode.FORMAT);
    }

    @Test
    void 길이_범위가_거꾸로면_설정을_만들_수_없다() {
        assertThatThrownBy(() -> new CheckSettings(40, 10, Map.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- LEAK

    @ParameterizedTest
    @ValueSource(strings = {
            "규칙: 평균 점수가 높은 편입니다",
            "방향: 평균 점수가 높은 편입니다",
            "평균 점수가 높은 편입니다 {{"})
    void 지시문_조각이_있으면_실패한다(String text) {
        assertThat(failed(text, item())).containsExactly(CheckCode.LEAK);
    }

    // ---- CHARSET

    @Test
    void label과_value_text의_외국_문자는_통과한다() {
        assertThat(failed("KPI가 전보다 높은 편입니다 (12ms)", item("KPI", "12ms"))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "KPI가 good 높은 편입니다",
            "KPI가 GOOD 높은 편입니다",
            "KPIs가 전보다 높은 편입니다",
            "KPI가 非常 높은 편입니다",
            "KPI가 とても 높은 편입니다",
            "KPI가 トップ 높은 편입니다",
            "KPI가 очень 높은 편입니다"})
    void label에_없는_외국_문자가_있으면_실패한다(String text) {
        assertThat(failed(text, item("KPI", "+12"))).containsExactly(CheckCode.CHARSET);
    }

    @Test
    void label과_value_text가_겹치면_긴_쪽을_지운다() {
        assertThat(failed("등급은 전과 같이 (AB)입니다", item("A", "AB"))).isEmpty();
        assertThat(failed("AB가 전보다 높은 편입니다", item("AB", "A"))).isEmpty();
    }

    // ---- 모으기

    @Test
    void 하나가_실패해도_6종을_다_돌리고_실패한_코드를_모두_모은다() {
        Item item = item("평균 점수", "+12.3%", Degree.MID, List.of("초보"));
        CheckReport report = checker.check("평균 점수가 초보자보다 5번 많은 편입니다", item, TemplateKind.SENTENCE);

        assertThat(report.passed()).isFalse();
        assertThat(report.results()).extracting(CheckResult::code).containsExactly(CheckCode.values());
        assertThat(report.failedCodes()).containsExactly(CheckCode.NUMBER, CheckCode.MUST_NOT);
    }

    @Test
    void 여섯_가지가_다_틀리면_코드_6개가_다_나온다() {
        Item item = item("평균 점수", "+12.3%", Degree.MID, List.of("초보"));
        assertThat(failed("규칙: 초보\n크게 good 5", item)).containsExactly(CheckCode.values());
    }

    @Test
    void 다_맞으면_통과한다() {
        CheckReport report = checker.check(GOOD, item(), TemplateKind.SENTENCE);
        assertThat(report.passed()).isTrue();
        assertThat(report.failedCodes()).isEmpty();
    }
}
