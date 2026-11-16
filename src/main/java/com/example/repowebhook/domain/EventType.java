package com.example.repowebhook.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.stream.Collectors;

/** Events a webhook can subscribe to. Wire names follow GitHub's ("push", "pull_request", "*"). */
public enum EventType {
    WILDCARD("*"),
    PING("ping"),
    PUSH("push"),
    PULL_REQUEST("pull_request"),
    ISSUES("issues"),
    ISSUE_COMMENT("issue_comment"),
    CREATE("create"),
    DELETE("delete"),
    RELEASE("release"),
    STAR("star");

    private final String value;

    EventType(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static EventType fromValue(String value) {
        return Arrays.stream(values())
                .filter(e -> e.value.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown event '" + value + "'. Supported: "
                        + Arrays.stream(values()).map(EventType::value).collect(Collectors.joining(", "))));
    }
}
