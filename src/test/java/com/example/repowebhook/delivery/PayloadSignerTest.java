package com.example.repowebhook.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PayloadSignerTest {

    // Test vector from GitHub's "Validating webhook deliveries" docs.
    private static final String SECRET = "It's a Secret to Everybody";
    private static final byte[] BODY = "Hello, World!".getBytes(StandardCharsets.UTF_8);
    private static final String EXPECTED = "sha256=757107ea0eb2509fc211221cce984b8a37570b6d7586c22c46f4379c8b043e17";

    @Test
    void signsWithHmacSha256() {
        assertThat(PayloadSigner.sign(SECRET, BODY)).isEqualTo(EXPECTED);
    }

    @Test
    void verifiesMatchingSignature() {
        assertThat(PayloadSigner.verify(SECRET, BODY, EXPECTED)).isTrue();
    }

    @Test
    void rejectsTamperedBodyWrongSecretOrMissingHeader() {
        assertThat(PayloadSigner.verify(SECRET, "Hello, World?".getBytes(StandardCharsets.UTF_8), EXPECTED)).isFalse();
        assertThat(PayloadSigner.verify("other", BODY, EXPECTED)).isFalse();
        assertThat(PayloadSigner.verify(SECRET, BODY, null)).isFalse();
        assertThat(PayloadSigner.verify(SECRET, BODY, EXPECTED.substring("sha256=".length()))).isFalse();
    }
}
