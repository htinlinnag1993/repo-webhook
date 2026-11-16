package com.example.repowebhook.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DeliveryPropertiesTest {

    private final DeliveryProperties props = new DeliveryProperties(Duration.ofSeconds(5), Duration.ofSeconds(10), 5,
            Duration.ofSeconds(10), 2.0, Duration.ofMinutes(1), Duration.ofSeconds(30), 4096, "test", true);

    @Test
    void backoffGrowsExponentiallyAndIsCapped() {
        assertThat(props.backoffAfter(1)).isEqualTo(Duration.ofSeconds(10));
        assertThat(props.backoffAfter(2)).isEqualTo(Duration.ofSeconds(20));
        assertThat(props.backoffAfter(3)).isEqualTo(Duration.ofSeconds(40));
        assertThat(props.backoffAfter(4)).isEqualTo(Duration.ofMinutes(1));
        assertThat(props.backoffAfter(20)).isEqualTo(Duration.ofMinutes(1));
    }
}
