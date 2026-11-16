package com.example.repowebhook.api;

import com.example.repowebhook.api.dto.PublishEventRequest;
import com.example.repowebhook.api.dto.PublishEventResponse;
import com.example.repowebhook.api.dto.PublishEventResponse.QueuedDelivery;
import com.example.repowebhook.service.EventService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simulates "something happened on this repo". Returns 202 with the deliveries that were queued;
 * the HTTP calls to subscribers happen asynchronously.
 */
@RestController
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping("/api/repos/{owner}/{repo}/events")
    public ResponseEntity<PublishEventResponse> publish(@PathVariable String owner, @PathVariable String repo,
                                                        @Valid @RequestBody PublishEventRequest request) {
        var queued = eventService.publish(owner, repo, request.event(), request.action(), request.payload()).stream()
                .map(d -> new QueuedDelivery(d.getWebhookId(), d.getId(), d.getGuid()))
                .toList();
        return ResponseEntity.accepted().body(new PublishEventResponse(request.event(), queued));
    }
}
