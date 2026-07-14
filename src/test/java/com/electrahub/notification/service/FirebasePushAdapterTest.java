package com.electrahub.notification.service;

import com.electrahub.notification.domain.Channel;
import com.electrahub.notification.domain.ContactStatus;
import com.electrahub.notification.domain.DeliveryStatus;
import com.electrahub.notification.domain.NotificationMessage;
import com.electrahub.notification.domain.PushDeviceRegistration;
import com.electrahub.notification.repository.NotificationMessageRepository;
import com.electrahub.notification.repository.PushDeviceRegistrationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FirebasePushAdapterTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-17T00:00:00Z"), ZoneOffset.UTC);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void disabledPushFailsWithoutCallingFirebase() throws Exception {
        NotificationMessageRepository repository = mock(NotificationMessageRepository.class);
        PushDeviceRegistrationRepository pushDeviceRepository = mock(PushDeviceRegistrationRepository.class);
        FirebasePushSender sender = mock(FirebasePushSender.class);
        FirebasePushAdapter adapter = new FirebasePushAdapter(repository, pushDeviceRepository, OBJECT_MAPPER, sender, CLOCK, false, 500, 5);

        DispatchResult result = adapter.dispatch(message("{}"));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo("PUSH_DISABLED");
        verify(sender, never()).send(any());
    }

    @Test
    void unconfiguredFirebaseFailsWithoutCallingProvider() throws Exception {
        NotificationMessageRepository repository = mock(NotificationMessageRepository.class);
        PushDeviceRegistrationRepository pushDeviceRepository = mock(PushDeviceRegistrationRepository.class);
        FirebasePushAdapter adapter = new FirebasePushAdapter(
                repository,
                pushDeviceRepository,
                OBJECT_MAPPER,
                FirebasePushSender.unconfigured(),
                CLOCK,
                true,
                500,
                5
        );

        DispatchResult result = adapter.dispatch(message("{}"));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo("FIREBASE_NOT_CONFIGURED");
        assertThat(result.retryable()).isTrue();
    }

    @Test
    void exhaustedDailyQuotaFailsWithoutCallingProvider() throws Exception {
        NotificationMessageRepository repository = mock(NotificationMessageRepository.class);
        PushDeviceRegistrationRepository pushDeviceRepository = mock(PushDeviceRegistrationRepository.class);
        FirebasePushSender sender = configuredSender();
        when(repository.countDispatchedSince(eq(Channel.PUSH), eq(DeliveryStatus.DISPATCHED), any())).thenReturn(500L);
        FirebasePushAdapter adapter = new FirebasePushAdapter(repository, pushDeviceRepository, OBJECT_MAPPER, sender, CLOCK, true, 500, 5);

        DispatchResult result = adapter.dispatch(message("{\"fcmToken\":\"token-1\"}"));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("PUSH_DAILY_QUOTA_EXCEEDED");
        verify(sender, never()).send(any());
    }

    @Test
    void sendsFirebaseMessageUsingPayloadToken() throws Exception {
        NotificationMessageRepository repository = mock(NotificationMessageRepository.class);
        PushDeviceRegistrationRepository pushDeviceRepository = mock(PushDeviceRegistrationRepository.class);
        FirebasePushSender sender = configuredSender();
        when(sender.send(any())).thenReturn("projects/electrahub/messages/123");
        when(repository.countDispatchedSince(eq(Channel.PUSH), eq(DeliveryStatus.DISPATCHED), any())).thenReturn(0L);
        FirebasePushAdapter adapter = new FirebasePushAdapter(repository, pushDeviceRepository, OBJECT_MAPPER, sender, CLOCK, true, 500, 5);

        DispatchResult result = adapter.dispatch(message("""
                {
                  "fcmToken": "token-1",
                  "title": "Session update",
                  "body": "Charging started",
                  "sessionId": "session-1"
                }
                """));

        assertThat(result.success()).isTrue();
        assertThat(result.provider()).isEqualTo("firebase-fcm");
        assertThat(result.providerMessageId()).isEqualTo("projects/electrahub/messages/123");
        verify(sender).send(any(Message.class));
    }

    @Test
    void sendsFirebaseMessageUsingRegisteredDeviceToken() throws Exception {
        NotificationMessageRepository repository = mock(NotificationMessageRepository.class);
        PushDeviceRegistrationRepository pushDeviceRepository = mock(PushDeviceRegistrationRepository.class);
        FirebasePushSender sender = configuredSender();
        PushDeviceRegistration device = new PushDeviceRegistration("tenant-1", "user-1", "device-1", "ios", "firebase");
        device.updateToken("registered-token", "hash", "reg...ken");
        when(sender.send(any())).thenReturn("projects/electrahub/messages/456");
        when(repository.countDispatchedSince(eq(Channel.PUSH), eq(DeliveryStatus.DISPATCHED), any())).thenReturn(0L);
        when(pushDeviceRepository.findFirstByTenantIdAndProviderAndDeviceIdAndStatusOrderByLastSeenAtDesc("tenant-1", "firebase", "device-1", ContactStatus.ACTIVE))
                .thenReturn(Optional.of(device));
        FirebasePushAdapter adapter = new FirebasePushAdapter(repository, pushDeviceRepository, OBJECT_MAPPER, sender, CLOCK, true, 500, 5);

        NotificationMessage message = new NotificationMessage("tenant-1", "event-1", "key-1", "device-1", Channel.PUSH, "template");
        message.setPayloadJson("{\"title\":\"Session update\"}");
        DispatchResult result = adapter.dispatch(message);

        assertThat(result.success()).isTrue();
        assertThat(result.providerMessageId()).isEqualTo("projects/electrahub/messages/456");
        verify(sender).send(any(Message.class));
    }

    @Test
    void transientProviderFailureIsMarkedRetryable() throws Exception {
        NotificationMessageRepository repository = mock(NotificationMessageRepository.class);
        PushDeviceRegistrationRepository pushDeviceRepository = mock(PushDeviceRegistrationRepository.class);
        FirebasePushSender sender = configuredSender();
        when(repository.countDispatchedSince(eq(Channel.PUSH), eq(DeliveryStatus.DISPATCHED), any())).thenReturn(0L);
        when(sender.send(any())).thenThrow(new IllegalStateException("temporary outage"));
        FirebasePushAdapter adapter = new FirebasePushAdapter(repository, pushDeviceRepository, OBJECT_MAPPER, sender, CLOCK, true, 500, 5);

        DispatchResult result = adapter.dispatch(message("{\"fcmToken\":\"token-1\"}"));

        assertThat(result.success()).isFalse();
        assertThat(result.retryable()).isTrue();
        assertThat(result.error()).contains("temporary outage");
    }

    @Test
    void missingApnsCredentialsFailWithoutRetrying() throws Exception {
        NotificationMessageRepository repository = mock(NotificationMessageRepository.class);
        PushDeviceRegistrationRepository pushDeviceRepository = mock(PushDeviceRegistrationRepository.class);
        FirebasePushSender sender = configuredSender();
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(MessagingErrorCode.THIRD_PARTY_AUTH_ERROR);
        when(exception.getMessage()).thenReturn("APNs authentication key is missing");
        when(repository.countDispatchedSince(eq(Channel.PUSH), eq(DeliveryStatus.DISPATCHED), any())).thenReturn(0L);
        when(sender.send(any())).thenThrow(exception);
        FirebasePushAdapter adapter = new FirebasePushAdapter(repository, pushDeviceRepository, OBJECT_MAPPER, sender, CLOCK, true, 500, 5);

        DispatchResult result = adapter.dispatch(message("{\"fcmToken\":\"token-1\"}"));

        assertThat(result.success()).isFalse();
        assertThat(result.retryable()).isFalse();
        assertThat(result.error()).contains("THIRD_PARTY_AUTH_ERROR");
    }

    private NotificationMessage message(String payloadJson) {
        NotificationMessage message = new NotificationMessage("tenant-1", "event-1", "key-1", "recipient-token", Channel.PUSH, "template");
        message.setSubject("Subject");
        message.setBody("Body");
        message.setPayloadJson(payloadJson);
        return message;
    }

    private FirebasePushSender configuredSender() {
        FirebasePushSender sender = mock(FirebasePushSender.class);
        when(sender.configured()).thenReturn(true);
        return sender;
    }
}
