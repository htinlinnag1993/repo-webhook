package com.example.repowebhook.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** How the payload is encoded in the request body, mirroring GitHub's "json" / "form" options. */
public enum ContentType {
    /** Body is the raw JSON payload. */
    JSON("json", "application/json"),
    /** Body is {@code payload=<url-encoded JSON>}. */
    FORM("form", "application/x-www-form-urlencoded");

    private final String value;
    private final String mediaType;

    ContentType(String value, String mediaType) {
        this.value = value;
        this.mediaType = mediaType;
    }

    @JsonValue
    public String value() {
        return value;
    }

    public String mediaType() {
        return mediaType;
    }

    @JsonCreator
    public static ContentType fromValue(String value) {
        return Arrays.stream(values())
                .filter(c -> c.value.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("content_type must be 'json' or 'form'"));
    }
}
