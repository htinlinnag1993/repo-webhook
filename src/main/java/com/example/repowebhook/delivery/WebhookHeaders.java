package com.example.repowebhook.delivery;

/** Headers sent with every delivery. */
public final class WebhookHeaders {

    /** Event name, e.g. {@code push}. */
    public static final String EVENT = "X-Webhook-Event";
    /** Unique delivery GUID — receivers should use it to de-duplicate retries. */
    public static final String DELIVERY = "X-Webhook-Delivery";
    /** Id of the webhook configuration that triggered this delivery. */
    public static final String HOOK_ID = "X-Webhook-Hook-ID";
    /** HMAC-SHA256 of the body, {@code sha256=<hex>}. Only sent when the hook has a secret. */
    public static final String SIGNATURE_256 = "X-Hub-Signature-256";

    private WebhookHeaders() {
    }
}
