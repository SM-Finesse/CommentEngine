package com.commentengine.api;

import java.util.Map;

public record SectionResponse(
        String text,
        SectionStatus status,
        int attempts,
        Map<String, Integer> checks,
        Versions versions) {
}
