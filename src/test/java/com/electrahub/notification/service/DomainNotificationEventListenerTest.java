package com.electrahub.notification.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @Test
    void repeatedChargingMilestoneUsesOneSessionScopedIdempotencyKey() {
        DomainNotificationEventListener listener = listener();
        when(orchestrator.submit(any())).thenReturn(List.of());

        listener.onDomainEvent(event("event-1", "CHARGING_IDLE_STARTED", "session-1"));
        listener.onDomainEvent(event("event-2", "CHARGING_IDLE_STARTED", "session-1"));

        ArgumentCaptor<NotificationDtos.SubmitNotificationRequest> requests =
                ArgumentCaptor.forClass(NotificationDtos.SubmitNotificationRequest.class);
        verify(orchestrator, times(2)).submit(requests.capture());

        assertThat(requests.getAllValues())
                .extracting(NotificationDtos.SubmitNotificationRequest::idempotencyKey)
                .containsExactly(
                        requests.getAllValues().get(0).idempotencyKey(),
                        requests.getAllValues().get(0).idempotencyKey()
                );
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

    private DomainNotificationEventListener listener() {
        return new DomainNotificationEventListener(orchestrator, "https://driver.electrahub.net/reset-password");
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
