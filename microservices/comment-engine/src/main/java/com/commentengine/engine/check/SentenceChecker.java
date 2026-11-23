package com.commentengine.engine.check;

import com.commentengine.api.Item;
import java.lang.Character.UnicodeScript;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 모델이 돌려준 문장의 범용 검사 6종.
 *
 * <p>문자열만 보고 판정한다. 생성 서버를 부르지 않고, 분야 지식도 갖지 않는다.
 * 하나가 실패해도 6종을 모두 돌린다. 상태가 바뀌지 않아 여러 스레드가 함께 써도 된다.
 */
public final class SentenceChecker {

    private static final Pattern LINE_BREAK = Pattern.compile("\\R");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern DIGIT_OR_PAREN = Pattern.compile("[0-9()]");

    /** 문장에 남아 있으면 안 되는 문자 — 라틴, 한자, 가나, 키릴 */
    private static final Set<UnicodeScript> FOREIGN = Set.of(
            UnicodeScript.LATIN,
            UnicodeScript.HAN,
            UnicodeScript.HIRAGANA,
            UnicodeScript.KATAKANA,
            UnicodeScript.CYRILLIC);

    private final CheckSettings settings;

    public SentenceChecker(CheckSettings settings) {
        this.settings = settings;
    }

    /** 검사 6종을 모두 돌린다. text가 null이면 빈 문장으로 본다 */
    public CheckReport check(String text, Item item, TemplateKind kind) {
        String sentence = text == null ? "" : text;
        List<CheckResult> results = new ArrayList<>();
        for (CheckCode code : CheckCode.values()) {
            boolean passed = switch (code) {
                case FORMAT -> formatOk(sentence);
                case NUMBER -> numberOk(sentence, item, kind);
                case DEGREE -> !containsAny(sentence, settings.forbiddenFor(item.degree()));
                case MUST_NOT -> !containsAny(sentence, item.mustNot());
                case LEAK -> !containsAny(sentence, settings.leakMarkers());
                case CHARSET -> charsetOk(sentence, item);
            };
            results.add(new CheckResult(code, passed));
        }
        return new CheckReport(results);
    }

    private boolean formatOk(String text) {
        if (text.isBlank() || LINE_BREAK.matcher(text).find()) {
            return false;
        }
        int length = text.codePointCount(0, text.length());
        return length >= settings.minLength() && length <= settings.maxLength();
    }

    /**
     * 허용되는 것(label, 괄호로 감싼 value_text)을 지우고 나서 숫자나 괄호가 남으면 실패.
     *
     * <p>괄호 밖 숫자, value_text와 다른 괄호, 겹친 괄호, 짝이 안 맞는 괄호가 모두 여기에 걸린다.
     * label과 인용을 한 번에 왼쪽부터 지우므로, label이 value_text 안에 들어 있어도("점"과 "12점") 인용이 깨지지 않는다.
     */
    private static boolean numberOk(String text, Item item, TemplateKind kind) {
        String label = Pattern.quote(item.label());
        if (kind == TemplateKind.SUMMARY && DIGIT.matcher(text.replaceAll(label, " ")).find()) {
            return false;
        }
        String quotedValue = "\\(\\s*" + Pattern.quote(item.valueText().strip()) + "\\s*\\)";
        String rest = text.replaceAll(label + "|" + quotedValue, " ");
        return !DIGIT_OR_PAREN.matcher(rest).find();
    }

    private static boolean charsetOk(String text, Item item) {
        // 같은 자리에서 시작하면 긴 쪽을 지운다
        boolean labelLonger = item.label().length() >= item.valueText().length();
        String longer = labelLonger ? item.label() : item.valueText();
        String shorter = labelLonger ? item.valueText() : item.label();
        String rest = text.replaceAll(Pattern.quote(longer) + "|" + Pattern.quote(shorter), " ");
        return rest.codePoints().mapToObj(UnicodeScript::of).noneMatch(FOREIGN::contains);
    }

    /** 낱말 중 하나라도 들어 있는가 (부분 일치). 빈 낱말은 모든 문장에 걸리므로 건너뛴다 */
    private static boolean containsAny(String text, List<String> words) {
        return words != null
                && words.stream().anyMatch(w -> w != null && !w.isBlank() && text.contains(w));
    }
}
