package com.example.repowebhook.delivery;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 signatures in the {@code sha256=<hex>} format used by the {@code X-Hub-Signature-256}
 * header. The signature covers the exact bytes of the request body.
 */
public final class PayloadSigner {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String PREFIX = "sha256=";

    private PayloadSigner() {
    }

    public static String sign(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return PREFIX + HexFormat.of().formatHex(mac.doFinal(body));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    /** Constant-time comparison, so receivers can't be timed to leak the expected signature. */
    public static boolean verify(String secret, byte[] body, String signatureHeader) {
        if (signatureHeader == null || !signatureHeader.startsWith(PREFIX)) {
            return false;
        }
        byte[] expected = sign(secret, body).getBytes(StandardCharsets.UTF_8);
        byte[] actual = signatureHeader.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }
}
