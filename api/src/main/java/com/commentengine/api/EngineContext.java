package com.commentengine.api;

import java.util.List;

public record EngineContext(
        String name,
        List<String> rules) {
}
