package com.electrahub.notification.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PrivacyHashServiceTest {
    private final PrivacyHashService service = new PrivacyHashService("test-salt");

    @Test
    void hashesAreDeterministicAndSalted() {
        assertThat(service.sha256("driver@example.com")).isEqualTo(service.sha256("driver@example.com"));
        assertThat(service.sha256("driver@example.com")).isNotEqualTo(service.sha256("other@example.com"));
    }

    @Test
    void masksEmailWithoutLeakingFullAddress() {
        assertThat(service.maskEmail("sysadmin.dev@electrahub.com")).isEqualTo("sy***@electrahub.com");
    }

    @Test
    void masksPushToken() {
        assertThat(service.maskToken("abcdef1234567890")).isEqualTo("abcd***7890");
    }
}
