package com.electrahub.notification.service;

import com.electrahub.notification.domain.Channel;
import com.electrahub.notification.domain.ContactStatus;
import com.electrahub.notification.domain.DeliveryStatus;
import com.electrahub.notification.domain.NotificationMessage;
import com.electrahub.notification.domain.PushDeviceRegistration;
import com.electrahub.notification.repository.NotificationMessageRepository;
import com.electrahub.notification.repository.PushDeviceRegistrationRepository;
import com.electrahub.notification.repository.UserContactRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
    PushDeviceRegistrationRepository pushDeviceRepository;
    @Mock
    RabbitTemplate rabbitTemplate;
    @Mock
    InboxRealtimePublisher inboxRealtimePublisher;

    @Test
    void submitCreatesOneNotificationPerDistinctChannelAndQueuesDispatch() {
        NotificationOrchestrator orchestrator = new NotificationOrchestrator(
                notificationRepository,
                contactRepository,
                pushDeviceRepository,
                rabbitTemplate,
                new ObjectMapper(),
                new PrivacyHashService("salt"),
                inboxRealtimePublisher,
                "notifications.events",
                "notifications.dispatch",
                "support@electrahub.net"
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
                pushDeviceRepository,
                rabbitTemplate,
                new ObjectMapper(),
                new PrivacyHashService("salt"),
                inboxRealtimePublisher,
                "notifications.events",
                "notifications.dispatch",
                "support@electrahub.net"
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

    @Test
    void registerPushDeviceStoresMaskedTokenAndActivatesDevice() {
        NotificationOrchestrator orchestrator = new NotificationOrchestrator(
                notificationRepository,
                contactRepository,
                pushDeviceRepository,
                rabbitTemplate,
                new ObjectMapper(),
                new PrivacyHashService("salt"),
                inboxRealtimePublisher,
                "notifications.events",
                "notifications.dispatch",
                "support@electrahub.net"
        );
        when(pushDeviceRepository.findByTenantIdAndUserIdAndProviderAndDeviceId("tenant-1", "user-1", "firebase", "device-1"))
                .thenReturn(Optional.empty());
        when(pushDeviceRepository.save(any(PushDeviceRegistration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = orchestrator.registerPushDevice(
                "tenant-1",
                "user-1",
                new NotificationDtos.PushDeviceRegistrationRequest(
                "device-1",
                "iOS",
                "very-long-firebase-token-value",
                null
        ));

        assertThat(response.provider()).isEqualTo("firebase");
        assertThat(response.platform()).isEqualTo("ios");
        assertThat(response.maskedToken()).doesNotContain("very-long-firebase-token-value");
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void submitPushFansOutToActiveRegisteredDevices() {
        NotificationOrchestrator orchestrator = new NotificationOrchestrator(
                notificationRepository,
                contactRepository,
                pushDeviceRepository,
                rabbitTemplate,
                new ObjectMapper(),
                new PrivacyHashService("salt"),
                inboxRealtimePublisher,
                "notifications.events",
                "notifications.dispatch",
                "support@electrahub.net"
        );
        PushDeviceRegistration device = new PushDeviceRegistration("tenant-1", "user-1", "device-1", "ios", "firebase");
        device.updateToken("firebase-token", "hash", "fire...oken");
        when(pushDeviceRepository.findByTenantIdAndUserIdAndProviderAndStatus("tenant-1", "user-1", "firebase", ContactStatus.ACTIVE))
                .thenReturn(List.of(device));
        when(notificationRepository.findByIdempotencyKeyAndChannelAndRecipientRef(any(), eq(Channel.PUSH), eq("device-1")))
                .thenReturn(Optional.empty());
        when(notificationRepository.save(any(NotificationMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var responses = orchestrator.submit(new NotificationDtos.SubmitNotificationRequest(
                "tenant-1",
                "event-1",
                "idem-1",
                "user-1",
                List.of(Channel.PUSH),
                "session-started",
                "Session started",
                "Charging started.",
                Map.of("sessionId", "s1")
        ));

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).recipientRef()).isEqualTo("device-1");
        verify(rabbitTemplate).convertAndSend(eq("notifications.events"), eq("notifications.dispatch"), any(DispatchCommand.class));
    }

    @Test
    void submitProjectBriefCreatesInboxAndEmailForSupportRecipient() {
        NotificationOrchestrator orchestrator = new NotificationOrchestrator(
                notificationRepository,
                contactRepository,
                pushDeviceRepository,
                rabbitTemplate,
                new ObjectMapper(),
                new PrivacyHashService("salt"),
                inboxRealtimePublisher,
                "notifications.events",
                "notifications.dispatch",
                "support@electrahub.net"
        );
        when(notificationRepository.findByIdempotencyKeyAndChannelAndRecipientRef(any(), any(), eq("support@electrahub.net")))
                .thenReturn(Optional.empty());
        when(notificationRepository.save(any(NotificationMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = orchestrator.submitProjectBrief(new NotificationDtos.ProjectBriefSubmissionRequest(
                "Amol Surjuse",
                "amol@example.com",
                "ElectraHub",
                "+1 555 0100",
                "Fleet depot",
                "We need a two-phase charging rollout plan for a mixed AC/DC site.",
                "org-page-contact",
                true,
                "google",
                "cpc",
                "fleet",
                ""
        ), "203.0.113.10");

        assertThat(response.status()).isEqualTo("ACCEPTED");
        assertThat(response.referenceId()).startsWith("project-brief-");
        assertThat(response.notifications()).hasSize(2);
        assertThat(response.notifications()).anySatisfy(notification -> {
            assertThat(notification.channel()).isEqualTo(Channel.IN_APP);
            assertThat(notification.recipientRef()).isEqualTo("support@electrahub.net");
            assertThat(notification.subject()).isEqualTo("New ElectraHub project brief");
            assertThat(notification.body()).contains("two-phase charging rollout");
            assertThat(notification.payloadJson()).contains("\"siteType\":\"Fleet depot\"");
        });
        verify(rabbitTemplate, times(2)).convertAndSend(eq("notifications.events"), eq("notifications.dispatch"), any(DispatchCommand.class));
    }

    @Test
    void driverInboxIsTenantScopedAndReturnsUnreadCount() {
        NotificationOrchestrator orchestrator = orchestrator();
        NotificationMessage message = new NotificationMessage(
                "tenant-1", "event-1", "key-1", "user-1", Channel.IN_APP, "charging-session-started");
        when(notificationRepository.findByTenantIdAndRecipientRefAndChannelAndReadAtIsNull(
                eq("tenant-1"), eq("user-1"), eq(Channel.IN_APP), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(message)));
        when(notificationRepository.countByTenantIdAndRecipientRefAndChannelAndReadAtIsNull(
                "tenant-1", "user-1", Channel.IN_APP)).thenReturn(1L);

        var response = orchestrator.inboxForUser(
                "tenant-1", "user-1", NotificationDtos.InboxReadState.UNREAD, 0, 30);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).recipientRef()).isEqualTo("user-1");
        assertThat(response.unreadCount()).isEqualTo(1);
    }

    @Test
    void driverCanMarkOwnInboxNotificationReadAndUnread() {
        NotificationOrchestrator orchestrator = orchestrator();
        UUID id = UUID.randomUUID();
        NotificationMessage message = new NotificationMessage(
                "tenant-1", "event-1", "key-1", "user-1", Channel.IN_APP, "charging-session-started");
        when(notificationRepository.findByIdAndTenantIdAndRecipientRefAndChannel(
                id, "tenant-1", "user-1", Channel.IN_APP)).thenReturn(Optional.of(message));
        when(notificationRepository.save(message)).thenReturn(message);

        var read = orchestrator.setInboxReadState(id, "tenant-1", "user-1", true);
        var unread = orchestrator.setInboxReadState(id, "tenant-1", "user-1", false);

        assertThat(read.readAt()).isNotNull();
        assertThat(unread.readAt()).isNull();
        verify(inboxRealtimePublisher, times(2)).updated(message);
    }

    @Test
    void markAllReadPublishesOneRealtimeInvalidation() {
        NotificationOrchestrator orchestrator = orchestrator();
        when(notificationRepository.markAllInboxNotificationsRead(
                eq("tenant-1"), eq("user-1"), eq(Channel.IN_APP), eq(DeliveryStatus.READ), any()))
                .thenReturn(3);
        when(notificationRepository.countByTenantIdAndRecipientRefAndChannelAndReadAtIsNull(
                "tenant-1", "user-1", Channel.IN_APP)).thenReturn(0L);

        var response = orchestrator.markAllInboxRead("tenant-1", "user-1");

        assertThat(response.affectedCount()).isEqualTo(3);
        assertThat(response.unreadCount()).isZero();
        verify(inboxRealtimePublisher).readAll("tenant-1", "user-1");
    }

    private NotificationOrchestrator orchestrator() {
        return new NotificationOrchestrator(
                notificationRepository,
                contactRepository,
                pushDeviceRepository,
                rabbitTemplate,
                new ObjectMapper(),
                new PrivacyHashService("salt"),
                inboxRealtimePublisher,
                "notifications.events",
                "notifications.dispatch",
                "support@electrahub.net"
        );
    }
}
