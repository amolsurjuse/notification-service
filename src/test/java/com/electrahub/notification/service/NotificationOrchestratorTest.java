package com.electrahub.notification.service;

import com.electrahub.notification.domain.Channel;
import com.electrahub.notification.domain.NotificationMessage;
import com.electrahub.notification.repository.NotificationMessageRepository;
import com.electrahub.notification.repository.UserContactRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationOrchestratorTest {
    @Mock
    NotificationMessageRepository notificationRepository;
    @Mock
    UserContactRepository contactRepository;
    @Mock
    RabbitTemplate rabbitTemplate;

    @Test
    void submitCreatesOneNotificationPerDistinctChannelAndQueuesDispatch() {
        NotificationOrchestrator orchestrator = new NotificationOrchestrator(
                notificationRepository,
                contactRepository,
                rabbitTemplate,
                new ObjectMapper(),
                new PrivacyHashService("salt"),
                "notifications.events",
                "notifications.dispatch"
        );
        when(notificationRepository.findByIdempotencyKeyAndChannelAndRecipientRef(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(notificationRepository.save(any(NotificationMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var request = new NotificationDtos.SubmitNotificationRequest(
                "tenant-1",
                "event-1",
                null,
                "user-1",
                List.of(Channel.IN_APP, Channel.EMAIL, Channel.EMAIL),
                "session-started",
                "Session started",
                "Your charging session started.",
                Map.of("sessionId", "s1")
        );

        var responses = orchestrator.submit(request);

        assertThat(responses).hasSize(2);
        verify(rabbitTemplate, times(2)).convertAndSend(eq("notifications.events"), eq("notifications.dispatch"), any(DispatchCommand.class));
    }

    @Test
    void submitReusesExistingIdempotentNotification() {
        NotificationOrchestrator orchestrator = new NotificationOrchestrator(
                notificationRepository,
                contactRepository,
                rabbitTemplate,
                new ObjectMapper(),
                new PrivacyHashService("salt"),
                "notifications.events",
                "notifications.dispatch"
        );
        NotificationMessage existing = new NotificationMessage("tenant-1", "event-1", "key-1", "user-1", Channel.IN_APP, "template");
        when(notificationRepository.findByIdempotencyKeyAndChannelAndRecipientRef("key-1", Channel.IN_APP, "user-1"))
                .thenReturn(Optional.of(existing));

        var request = new NotificationDtos.SubmitNotificationRequest(
                "tenant-1",
                "event-1",
                "key-1",
                "user-1",
                List.of(Channel.IN_APP),
                "template",
                null,
                null,
                Map.of()
        );

        var responses = orchestrator.submit(request);

        assertThat(responses).hasSize(1);
        verify(notificationRepository, never()).save(any());
        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }
}
