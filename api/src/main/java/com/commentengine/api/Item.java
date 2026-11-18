package com.commentengine.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record Item(
        @NotBlank String key,
        @NotBlank String label,
        @NotBlank String meaning,
        @NotBlank @JsonProperty("value_text") String valueText,
        @NotBlank String direction,
        @NotNull Degree degree,
        @NotBlank String reliability,
        List<String> rules,
        @JsonProperty("must_not") List<String> mustNot) {
}
