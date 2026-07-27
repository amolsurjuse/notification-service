package com.electrahub.notification.config;

import com.electrahub.notification.service.FirebasePushSender;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.health.contributor.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChannelAdapterConfigTest {
    private final ChannelAdapterConfig config = new ChannelAdapterConfig();

    @Test
    void skipsCredentialVerificationWhenPushDeliveryIsDisabled() throws Exception {
        FirebasePushSender sender = mock(FirebasePushSender.class);
        ApplicationRunner runner = config.firebaseCredentialVerifier(sender, false);

        runner.run(mock(ApplicationArguments.class));

        verify(sender, never()).verifyCredentials();
    }

    @Test
    void failsStartupWhenPushDeliveryHasNoCredentials() {
        FirebasePushSender sender = mock(FirebasePushSender.class);
        when(sender.configured()).thenReturn(false);
        ApplicationRunner runner = config.firebaseCredentialVerifier(sender, true);

        assertThatThrownBy(() -> runner.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("credentials are not configured");
    }

    @Test
    void verifiesCredentialsWhenPushDeliveryIsEnabled() throws Exception {
        FirebasePushSender sender = mock(FirebasePushSender.class);
        when(sender.configured()).thenReturn(true);
        ApplicationRunner runner = config.firebaseCredentialVerifier(sender, true);

        runner.run(mock(ApplicationArguments.class));

        verify(sender).verifyCredentials();
    }

    @Test
    void exposesConfiguredFirebaseProjectInHealthDetails() {
        FirebasePushSender sender = mock(FirebasePushSender.class);
        when(sender.configured()).thenReturn(true);
        when(sender.projectId()).thenReturn("electrahub-87dec");

        var health = config.firebasePushHealthIndicator(sender, true).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("enabled", true);
        assertThat(health.getDetails()).containsEntry("projectId", "electrahub-87dec");
    }
}
