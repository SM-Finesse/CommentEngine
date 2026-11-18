package com.commentengine.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum SectionStatus {
    @JsonProperty("ok") OK,
    @JsonProperty("failed") FAILED
}
