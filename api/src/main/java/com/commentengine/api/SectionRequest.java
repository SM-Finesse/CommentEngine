package com.commentengine.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SectionRequest(
        @Valid EngineContext context,
        @NotNull @Valid Section section,
        @NotNull @Size(min = 1, max = 32) List<@Valid @NotNull Item> items,
        @NotNull @Min(1000) @Max(120000) @JsonProperty("deadline_ms") Integer deadlineMs) {
}
