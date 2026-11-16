# repo-webhook

A Spring Boot service for registering webhook endpoints on a repository and delivering signed event
payloads to them when something happens. The model and API are based on GitHub's repository webhooks.

- Register, update, list and delete webhooks per `owner/repo`, each subscribing to a set of events (or `*`)
- Deliveries are **async**, signed with **HMAC-SHA256** (`X-Hub-Signature-256`) and **retried with exponential backoff**
- Every attempt is recorded in a **delivery log**: request headers, payload, response status/body, duration
- **Redeliver** any past delivery; send a **ping** to test an endpoint (one is sent automatically on creation)
- Deliveries are stored before they're sent (outbox pattern), so they survive restarts
- Includes a **demo receiver** so you can try the whole flow with no other services

## Run

Requires JDK 17+ and Maven 3.9+.

```bash
mvn spring-boot:run      # http://localhost:8080   (H2 console at /h2-console, JDBC URL jdbc:h2:mem:webhooks)
mvn test
```

## Try it

```bash
# 1. Register a webhook (pointing at the built-in demo receiver). A "ping" is delivered immediately.
curl -s -X POST localhost:8080/api/repos/octo/hello/hooks -H 'Content-Type: application/json' -d '{
  "events": ["push", "pull_request"],
  "config": { "url": "http://localhost:8080/demo/receiver", "content_type": "json", "secret": "dev-secret" }
}'

# 2. Something happens on the repo
curl -s -X POST localhost:8080/api/repos/octo/hello/events -H 'Content-Type: application/json' -d '{
  "event": "pull_request", "action": "opened",
  "payload": { "number": 42, "pull_request": { "title": "Add webhooks", "head": { "ref": "feature" } } }
}'

# 3. Inspect the delivery log and a single delivery
curl -s localhost:8080/api/repos/octo/hello/hooks/1/deliveries
curl -s localhost:8080/api/repos/octo/hello/hooks/1/deliveries/2

# 4. See what the receiver got
curl -s localhost:8080/demo/receiver
```

## API

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/repos/{owner}/{repo}/hooks` | Create a webhook (201) |
| `GET` | `/api/repos/{owner}/{repo}/hooks` | List webhooks |
| `GET` | `/api/repos/{owner}/{repo}/hooks/{id}` | Get a webhook |
| `PATCH` | `/api/repos/{owner}/{repo}/hooks/{id}` | Update (partial; `"secret": ""` removes the secret) |
| `DELETE` | `/api/repos/{owner}/{repo}/hooks/{id}` | Delete a webhook and its delivery log (204) |
| `POST` | `/api/repos/{owner}/{repo}/hooks/{id}/pings` | Send a `ping` event (204) |
| `GET` | `/api/repos/{owner}/{repo}/hooks/{id}/deliveries?page=0&per_page=30` | Delivery log, newest first |
| `GET` | `/api/repos/{owner}/{repo}/hooks/{id}/deliveries/{deliveryId}` | Delivery detail incl. request and response |
| `POST` | `/api/repos/{owner}/{repo}/hooks/{id}/deliveries/{deliveryId}/attempts` | Redeliver (202) |
| `POST` | `/api/repos/{owner}/{repo}/events` | Publish an event; fans out to subscribed hooks (202) |

Events: `push`, `pull_request`, `issues`, `issue_comment`, `create`, `delete`, `release`, `star`, `*` (all).
Errors are returned as RFC 9457 `application/problem+json` (400 malformed, 404 not found, 422 invalid).

## Delivery format

```http
POST /your/endpoint HTTP/1.1
Content-Type: application/json
User-Agent: RepoWebhook-Hookshot/1.0
X-Webhook-Event: pull_request
X-Webhook-Delivery: 3f2b6a7e-...        # unique per delivery; de-duplicate on this
X-Webhook-Hook-ID: 1
X-Hub-Signature-256: sha256=...          # only if the hook has a secret

{ "action": "opened", "number": 42, "pull_request": { ... },
  "repository": { "name": "hello", "full_name": "octo/hello", "owner": { "login": "octo" } } }
```

With `"content_type": "form"` the body is instead `payload=<url-encoded JSON>`. The signature always
covers the exact raw body bytes.

**Receivers** should verify the signature with a constant-time compare before parsing the body, respond
with a 2xx within the timeout (10s), and handle the same `X-Webhook-Delivery` arriving more than once.
See `DemoReceiverController` for an example.

## How delivery works

```
POST /events ─▶ EventService ─▶ one WebhookDelivery row per subscribed hook (PENDING)   ┐ same
                                └▶ DeliveryEnqueuedEvent                                ┘ transaction
                                        │ after commit (@Async, "webhook-" thread pool)
                                        ▼
                              WebhookDispatcher.attempt(id)
                                1. claim: PENDING|RETRYING → IN_PROGRESS (atomic UPDATE, prevents double-send)
                                2. sign + POST
                                3. 2xx               → SUCCEEDED
                                   5xx/408/429/network error, attempts left → RETRYING (next_attempt_at = now + backoff)
                                   other 4xx or attempts exhausted          → FAILED
DeliveryRetryScheduler (every 5s) ─▶ re-dispatches RETRYING rows that are due, plus PENDING rows that were never picked up
```

Tune retries, timeouts and backoff under `webhook.delivery.*` in `application.yml`.

## Code layout

```
src/main/java/com/example/repowebhook
├── api/          REST controllers, DTOs, error handling
├── service/      WebhookService (registration), EventService (fan-out), DeliveryService (log/redeliver), URL validation
├── delivery/     WebhookDispatcher (HTTP + outcome), retry scheduler, async listener, PayloadSigner
├── domain/       JPA entities: Webhook, WebhookDelivery; enums
├── repository/   Spring Data repositories
├── config/       Executor, RestClient, typed properties
└── demo/         Sample receiver (disable with demo.receiver.enabled=false)
```

## Before production

This is a starting point. Before running it for real:

- **AuthN/AuthZ:** the API has no authentication. Add Spring Security and check that the caller administers `{owner}/{repo}`.
- **SSRF:** set `webhook.delivery.allow-private-addresses=false` so hooks can't target internal networks or cloud metadata endpoints.
- **Secrets at rest:** webhook secrets are stored in plaintext because the HMAC needs them. Encrypt the column (e.g. a JPA `AttributeConverter` backed by a KMS).
- **Database:** swap H2 for Postgres/MySQL, and use Flyway/Liquibase migrations instead of `ddl-auto: update`.
- **Multiple instances:** row claiming is already atomic, so several instances can share the work safely. Deliveries stuck `IN_PROGRESS` after a crash aren't reclaimed yet. Add a sweep for rows whose `last_attempt_at` is older than the read timeout.
- **Retention:** prune old delivery rows (GitHub keeps around 3 days).
- **Disable** the demo receiver (`demo.receiver.enabled=false`).
