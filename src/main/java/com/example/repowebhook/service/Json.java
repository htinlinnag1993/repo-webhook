package com.example.repowebhook.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** Thin wrapper so services don't have to handle Jackson's checked exceptions. */
@Component
public class Json {

    private final ObjectMapper objectMapper;

    public Json(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Payload is not serializable to JSON", e);
        }
    }
}
