package com.example.repowebhook.service;

/** Request is well-formed but semantically invalid (bad URL, disallowed event, ...). Maps to 422. */
public class InvalidWebhookException extends RuntimeException {

    public InvalidWebhookException(String message) {
        super(message);
    }
}
