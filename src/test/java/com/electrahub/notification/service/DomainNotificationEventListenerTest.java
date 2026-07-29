package com.electrahub.notification.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DomainNotificationEventListenerTest {
    @Mock
    NotificationOrchestrator orchestrator;

    @Mock
    CommunicationPreferenceService communicationPreferenceService;

    @Mock
    SensitiveEmailDispatchService sensitiveEmailDispatchService;

    @Test
    void repeatedChargingMilestoneUsesOneSessionScopedIdempotencyKey() {
        DomainNotificationEventListener listener = listener();
        when(orchestrator.submit(any())).thenReturn(List.of());

        listener.onDomainEvent(event("event-1", "CHARGING_IDLE_STARTED", "session-1"));
        listener.onDomainEvent(event("event-2", "CHARGING_IDLE_STARTED", "session-1"));

        ArgumentCaptor<NotificationDtos.SubmitNotificationRequest> requests =
                ArgumentCaptor.forClass(NotificationDtos.SubmitNotificationRequest.class);
        verify(orchestrator, times(4)).submit(requests.capture());

        assertThat(requests.getAllValues())
                .extracting(NotificationDtos.SubmitNotificationRequest::idempotencyKey)
                .containsOnly(requests.getAllValues().get(0).idempotencyKey());
        assertThat(requests.getAllValues().get(0).idempotencyKey()).startsWith("charging:");
    }

    @Test
    void differentChargingMilestonesUseDifferentIdempotencyKeys() {
        String idleKey = DomainNotificationEventListener.idempotencyKeyFor(
                event("event-1", "CHARGING_IDLE_STARTED", "session-1"),
                Map.of("sessionId", "session-1")
        );
        String batteryKey = DomainNotificationEventListener.idempotencyKeyFor(
                event("event-2", "CHARGING_BATTERY_FULL", "session-1"),
                Map.of("sessionId", "session-1")
        );

        assertThat(idleKey).isNotEqualTo(batteryKey);
    }

    @Test
    void nonChargingEventsKeepProducerEventIdentity() {
        NotificationDtos.DomainNotificationEvent event = event("event-1", "USER_PASSWORD_CHANGED", null);

        assertThat(DomainNotificationEventListener.idempotencyKeyFor(event, Map.of()))
                .isEqualTo("event-1:USER_PASSWORD_CHANGED");
    }

    @Test
    void lowBalanceStopCreatesPushAndInboxNotifications() {
        DomainNotificationEventListener listener = listener();
        when(orchestrator.submit(any())).thenReturn(List.of());

        listener.onDomainEvent(event("event-low-balance", "CHARGING_LOW_BALANCE_STOP", "session-1"));

        ArgumentCaptor<NotificationDtos.SubmitNotificationRequest> request =
                ArgumentCaptor.forClass(NotificationDtos.SubmitNotificationRequest.class);
        verify(orchestrator, times(2)).submit(request.capture());
        assertThat(request.getAllValues())
                .extracting(value -> value.channels().get(0))
                .containsExactly(
                        com.electrahub.notification.domain.Channel.PUSH,
                        com.electrahub.notification.domain.Channel.IN_APP
                );
        assertThat(request.getAllValues()).allSatisfy(value -> {
            assertThat(value.recipientRef()).isEqualTo("user-1");
            assertThat(value.templateId()).isEqualTo("charging-low-balance-stop");
            assertThat(value.subject()).containsIgnoringCase("low balance");
        });
    }

    @Test
    void preservesTheDomainEventTimeForAsynchronouslyDeliveredNotifications() {
        DomainNotificationEventListener listener = listener();
        when(orchestrator.submit(any())).thenReturn(List.of());
        OffsetDateTime occurredAt = OffsetDateTime.parse("2026-07-22T12:34:56Z");

        listener.onDomainEvent(new NotificationDtos.DomainNotificationEvent(
                "event-idle-fee-started",
                "CHARGING_IDLE_FEE_STARTED",
                "electrahub",
                "user-1",
                "user-1",
                occurredAt.toString(),
                Map.of("sessionId", "session-1")
        ));

        ArgumentCaptor<NotificationDtos.SubmitNotificationRequest> request =
                ArgumentCaptor.forClass(NotificationDtos.SubmitNotificationRequest.class);
        verify(orchestrator, times(2)).submit(request.capture());
        assertThat(request.getAllValues()).allSatisfy(value -> {
            assertThat(value.templateId()).isEqualTo("charging-idle-fee-started");
            assertThat(value.occurredAt()).isEqualTo(occurredAt);
            assertThat(value.subject()).isEqualTo("Idle fees have started");
        });
    }

    @Test
    void accountAndPaymentEventsCreateInboxNotificationsOnly() {
        DomainNotificationEventListener listener = listener();
        when(orchestrator.submit(any())).thenReturn(List.of());
        List<String> eventTypes = List.of(
                "PAYMENT_CARD_ADDED",
                "PAYMENT_CARD_REMOVED",
                "PAYMENT_AUTO_TOP_UP_ENABLED",
                "PAYMENT_AUTO_TOP_UP_DISABLED",
                "PAYMENT_AUTO_TOP_UP_UPDATED",
                "PAYMENT_AUTO_TOP_UP_COMPLETED",
                "PAYMENT_WALLET_TOP_UP_COMPLETED",
                "USER_PROFILE_UPDATED"
        );

        eventTypes.forEach(eventType -> listener.onDomainEvent(new NotificationDtos.DomainNotificationEvent(
                "event-" + eventType,
                eventType,
                "electrahub",
                "user-1",
                "user-1",
                "2026-07-15T12:00:00Z",
                Map.of(
                        "brand", "Visa",
                        "last4", "1111",
                        "cardBrand", "Visa",
                        "cardLast4", "1111",
                        "amount", "20.00",
                        "threshold", "30.00",
                        "newBalance", "42.00",
                        "currency", "USD"
                )
        )));

        ArgumentCaptor<NotificationDtos.SubmitNotificationRequest> requests =
                ArgumentCaptor.forClass(NotificationDtos.SubmitNotificationRequest.class);
        verify(orchestrator, times(eventTypes.size())).submit(requests.capture());
        assertThat(requests.getAllValues()).allSatisfy(request -> {
            assertThat(request.channels()).containsExactly(com.electrahub.notification.domain.Channel.IN_APP);
            assertThat(request.recipientRef()).isEqualTo("user-1");
            assertThat(request.subject()).isNotEqualTo("ElectraHub notification");
            assertThat(request.body()).isNotBlank();
        });
    }

    @Test
    void accountCreationDoesNotCreateAnEmailNotification() {
        DomainNotificationEventListener listener = listener();
        when(orchestrator.submit(any())).thenReturn(List.of());
        listener.onDomainEvent(event("event-account", "USER_ACCOUNT_CREATED", null));
        ArgumentCaptor<NotificationDtos.SubmitNotificationRequest> requests =
                ArgumentCaptor.forClass(NotificationDtos.SubmitNotificationRequest.class);
        verify(orchestrator, times(2)).submit(requests.capture());
        assertThat(requests.getAllValues())
                .extracting(value -> value.channels().get(0))
                .containsExactly(
                        com.electrahub.notification.domain.Channel.PUSH,
                        com.electrahub.notification.domain.Channel.IN_APP
                );
    }

    @Test
    void emailOtpUsesSensitiveDeliveryWithoutPersistingThroughOrchestrator() {
        DomainNotificationEventListener listener = listener();
        NotificationDtos.DomainNotificationEvent event = new NotificationDtos.DomainNotificationEvent(
                "otp-event", "USER_EMAIL_OTP_REQUESTED", "electrahub",
                "user@example.com", "user-1", "2026-07-15T12:00:00Z",
                Map.of("challengeId", "challenge-1", "code", "123456")
        );
        listener.onDomainEvent(event);
        verify(sensitiveEmailDispatchService).dispatchEmailOtp(event);
        verify(orchestrator, times(0)).submit(any());
    }

    @Test
    void disabledReceiptEmailStillCreatesPushAndInbox() {
        DomainNotificationEventListener listener = listener();
        when(orchestrator.submit(any())).thenReturn(List.of());
        when(communicationPreferenceService.shouldDeliverReceiptEmail("electrahub", "user-1")).thenReturn(false);
        listener.onDomainEvent(event("receipt-event", "CHARGING_RECEIPT_READY", "session-1"));
        ArgumentCaptor<NotificationDtos.SubmitNotificationRequest> requests =
                ArgumentCaptor.forClass(NotificationDtos.SubmitNotificationRequest.class);
        verify(orchestrator, times(2)).submit(requests.capture());
        assertThat(requests.getAllValues())
                .extracting(value -> value.channels().get(0))
                .containsExactly(
                        com.electrahub.notification.domain.Channel.PUSH,
                        com.electrahub.notification.domain.Channel.IN_APP
                );
    }

    private DomainNotificationEventListener listener() {
        return new DomainNotificationEventListener(
                orchestrator,
                communicationPreferenceService,
                sensitiveEmailDispatchService,
                "https://driver.electrahub.net/reset-password"
        );
    }

    private NotificationDtos.DomainNotificationEvent event(String eventId, String eventType, String sessionId) {
        Map<String, Object> payload = sessionId == null ? Map.of() : Map.of("sessionId", sessionId);
        return new NotificationDtos.DomainNotificationEvent(
                eventId,
                eventType,
                "electrahub",
                "user-1",
                "user-1",
                "2026-07-13T20:00:00Z",
                payload
        );
    }
}
