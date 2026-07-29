package com.electrahub.notification.service;

import com.electrahub.notification.domain.Channel;
import com.electrahub.notification.domain.NotificationMessage;
import com.electrahub.notification.repository.NotificationMessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SensitiveNotificationAuditServiceTest {
    @Test
    void otpCodeIsNeverStoredInTheNotificationAudit() {
        NotificationMessageRepository repository = mock(NotificationMessageRepository.class);
        when(repository.findByIdempotencyKeyAndChannelAndRecipientRef(
                "event-1:USER_EMAIL_OTP_REQUESTED", Channel.EMAIL, "user@example.com"))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(NotificationMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        SensitiveNotificationAuditService service =
                new SensitiveNotificationAuditService(repository, new ObjectMapper(), 4);
        NotificationDtos.DomainNotificationEvent event = new NotificationDtos.DomainNotificationEvent(
                "event-1",
                "USER_EMAIL_OTP_REQUESTED",
                "electrahub",
                "user@example.com",
                "user-1",
                "2026-07-28T12:00:00Z",
                Map.of("challengeId", "challenge-1", "code", "123456")
        );

        service.reserve(event);

        ArgumentCaptor<NotificationMessage> audit = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(repository).saveAndFlush(audit.capture());
        assertThat(audit.getValue().getBody()).isEqualTo("[SENSITIVE CONTENT REDACTED]");
        assertThat(audit.getValue().getPayloadJson())
                .contains("\"redacted\":true")
                .doesNotContain("123456")
                .doesNotContain("\"code\"");
    }
}
