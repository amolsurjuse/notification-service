package com.electrahub.notification.service;

import com.electrahub.notification.domain.Channel;
import com.electrahub.notification.domain.NotificationMessage;
import com.electrahub.notification.repository.NotificationMessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.messaging.Message;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

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
        FirebasePushSender sender = mock(FirebasePushSender.class);
        FirebasePushAdapter adapter = new FirebasePushAdapter(repository, OBJECT_MAPPER, sender, CLOCK, false, 500, 5);

        DispatchResult result = adapter.dispatch(message("{}"));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo("PUSH_DISABLED");
        verify(sender, never()).send(any());
    }

    @Test
    void unconfiguredFirebaseFailsWithoutCallingProvider() throws Exception {
        NotificationMessageRepository repository = mock(NotificationMessageRepository.class);
        FirebasePushAdapter adapter = new FirebasePushAdapter(
                repository,
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
    }

    @Test
    void exhaustedDailyQuotaFailsWithoutCallingProvider() throws Exception {
        NotificationMessageRepository repository = mock(NotificationMessageRepository.class);
        FirebasePushSender sender = configuredSender();
        when(repository.countAttemptedSince(eq(Channel.PUSH), any())).thenReturn(500L);
        FirebasePushAdapter adapter = new FirebasePushAdapter(repository, OBJECT_MAPPER, sender, CLOCK, true, 500, 5);

        DispatchResult result = adapter.dispatch(message("{\"fcmToken\":\"token-1\"}"));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("PUSH_DAILY_QUOTA_EXCEEDED");
        verify(sender, never()).send(any());
    }

    @Test
    void sendsFirebaseMessageUsingPayloadToken() throws Exception {
        NotificationMessageRepository repository = mock(NotificationMessageRepository.class);
        FirebasePushSender sender = configuredSender();
        when(sender.send(any())).thenReturn("projects/electrahub/messages/123");
        when(repository.countAttemptedSince(eq(Channel.PUSH), any())).thenReturn(0L);
        FirebasePushAdapter adapter = new FirebasePushAdapter(repository, OBJECT_MAPPER, sender, CLOCK, true, 500, 5);

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
