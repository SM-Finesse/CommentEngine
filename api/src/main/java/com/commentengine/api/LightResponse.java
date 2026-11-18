package com.commentengine.api;

import java.util.List;
import java.util.Map;

public record LightResponse(
        String summary,
        List<ItemSentence> items,
        List<String> failed,
        Map<String, Integer> checks,
        Versions versions) {
}
