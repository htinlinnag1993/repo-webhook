package com.example.repowebhook.service;

import com.example.repowebhook.config.DeliveryProperties;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import org.springframework.stereotype.Component;

/**
 * Validates subscriber URLs. Runs at registration time and again before every delivery, because DNS can
 * change after registration. With {@code allow-private-addresses=false} it rejects loopback, private and
 * link-local targets (e.g. 169.254.169.254 cloud metadata) — a basic SSRF guard.
 */
@Component
public class WebhookUrlValidator {

    private final DeliveryProperties properties;

    public WebhookUrlValidator(DeliveryProperties properties) {
        this.properties = properties;
    }

    public void validate(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new InvalidWebhookException("url is not a valid URI");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new InvalidWebhookException("url must use http or https");
        }
        if (uri.getHost() == null) {
            throw new InvalidWebhookException("url must include a host");
        }
        if (uri.getUserInfo() != null) {
            throw new InvalidWebhookException("url must not contain credentials");
        }
        if (!properties.allowPrivateAddresses()) {
            rejectPrivateAddresses(uri.getHost());
        }
    }

    private void rejectPrivateAddresses(String host) {
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isSiteLocalAddress()
                        || address.isLinkLocalAddress() || address.isMulticastAddress()) {
                    throw new InvalidWebhookException("url resolves to a private or reserved address");
                }
            }
        } catch (UnknownHostException e) {
            throw new InvalidWebhookException("url host could not be resolved");
        }
    }
}
