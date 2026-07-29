package com.electrahub.notification.service;

import com.electrahub.notification.domain.CommunicationPreference;
import com.electrahub.notification.repository.CommunicationPreferenceRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommunicationPreferenceServiceTest {
    @Test
    void verifiedUsersReceiveEnabledDefaults() {
        CommunicationPreferenceRepository repository = mock(CommunicationPreferenceRepository.class);
        UserPrincipalClient client = mock(UserPrincipalClient.class);
        when(client.get("user-1")).thenReturn(
                new UserPrincipalClient.UserPrincipal("user-1", "user@example.com", true));
        when(repository.findByTenantIdAndUserIdAndChannelAndTopic(
                anyString(), anyString(), anyString(), anyString())).thenReturn(Optional.empty());

        CommunicationPreferenceService service = new CommunicationPreferenceService(repository, client);

        NotificationDtos.CommunicationPreferencesResponse response = service.get("electrahub", "user-1");

        assertThat(response.channels()).singleElement().satisfies(email -> {
            assertThat(email.channel()).isEqualTo("EMAIL");
            assertThat(email.available()).isTrue();
            assertThat(email.enabled()).isTrue();
            assertThat(email.effectiveEnabled()).isTrue();
            assertThat(email.topics()).singleElement().satisfies(topic -> {
                assertThat(topic.topic()).isEqualTo("RECEIPT");
                assertThat(topic.effectiveEnabled()).isTrue();
            });
        });
    }

    @Test
    void unverifiedUsersCannotChangeEmailPreferences() {
        CommunicationPreferenceRepository repository = mock(CommunicationPreferenceRepository.class);
        UserPrincipalClient client = mock(UserPrincipalClient.class);
        when(client.get("user-1")).thenReturn(
                new UserPrincipalClient.UserPrincipal("user-1", "user@example.com", false));
        CommunicationPreferenceService service = new CommunicationPreferenceService(repository, client);

        assertThatThrownBy(() -> service.updateEmail(
                "electrahub", "user-1", new NotificationDtos.UpdateEmailPreferencesRequest(false, false)))
                .isInstanceOf(EmailNotVerifiedException.class);
    }

    @Test
    void verifiedReceiptResolvesProfileEmail() {
        CommunicationPreferenceRepository repository = mock(CommunicationPreferenceRepository.class);
        UserPrincipalClient client = mock(UserPrincipalClient.class);
        when(client.get("user-1")).thenReturn(
                new UserPrincipalClient.UserPrincipal("user-1", " user@example.com ", true));
        when(repository.findByTenantIdAndUserIdAndChannelAndTopic(
                anyString(), anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        CommunicationPreferenceService service = new CommunicationPreferenceService(repository, client);

        assertThat(service.resolveReceiptEmail("electrahub", "user-1"))
                .contains("user@example.com");
    }

    @Test
    void disabledReceiptOverrideHasNoRecipient() {
        CommunicationPreferenceRepository repository = mock(CommunicationPreferenceRepository.class);
        UserPrincipalClient client = mock(UserPrincipalClient.class);
        when(client.get("user-1")).thenReturn(
                new UserPrincipalClient.UserPrincipal("user-1", "user@example.com", true));
        when(repository.findByTenantIdAndUserIdAndChannelAndTopic(
                "electrahub", "user-1", "EMAIL", "ALL"))
                .thenReturn(Optional.empty());
        when(repository.findByTenantIdAndUserIdAndChannelAndTopic(
                "electrahub", "user-1", "EMAIL", "RECEIPT"))
                .thenReturn(Optional.of(new CommunicationPreference(
                        "electrahub", "user-1", "EMAIL", "RECEIPT", false)));
        CommunicationPreferenceService service = new CommunicationPreferenceService(repository, client);

        assertThat(service.resolveReceiptEmail("electrahub", "user-1")).isEmpty();
    }
}
