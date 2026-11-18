package com.commentengine.api;

public record ItemSentence(
        String key,
        String sentence,
        int attempts) {
}
