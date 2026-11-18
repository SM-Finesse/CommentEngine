package com.commentengine.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum Degree {
    @JsonProperty("strong") STRONG,
    @JsonProperty("weak") WEAK,
    @JsonProperty("mid") MID,
    @JsonProperty("zero") ZERO
}
