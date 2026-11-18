package com.commentengine.api;

import jakarta.validation.constraints.NotBlank;

public record Section(
        @NotBlank String key,
        @NotBlank String title,
        String description) {
}
