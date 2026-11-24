package com.commentengine.engine.check;

import com.commentengine.api.Degree;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 검사 설정. 틀마다 하나씩 있다.
 *
 * @param minLength       글자 수 아래 끝 (공백 포함, 코드포인트 기준, 이 값 포함)
 * @param maxLength       글자 수 위 끝 (이 값 포함)
 * @param degreeForbidden degree별 금지 표현. 없는 degree는 강도 검사를 하지 않음
 * @param leakMarkers     문장에 나오면 안 되는 지시문 조각
 */
public record CheckSettings(
        int minLength,
        int maxLength,
        Map<Degree, List<String>> degreeForbidden,
        List<String> leakMarkers) {

    public CheckSettings {
        if (minLength < 0 || minLength > maxLength) {
            throw new IllegalArgumentException("길이 범위가 잘못됨: " + minLength + "~" + maxLength);
        }
        Map<Degree, List<String>> copy = new EnumMap<>(Degree.class);
        degreeForbidden.forEach((degree, words) -> copy.put(degree, List.copyOf(words)));
        degreeForbidden = Collections.unmodifiableMap(copy);
        leakMarkers = List.copyOf(leakMarkers);
    }

    /** degree의 금지 표현. 정해 두지 않았으면 빈 목록 */
    List<String> forbiddenFor(Degree degree) {
        return degreeForbidden.getOrDefault(degree, List.of());
    }
}
