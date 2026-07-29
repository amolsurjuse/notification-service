package com.electrahub.notification.service;

import com.electrahub.notification.domain.Channel;
import com.electrahub.notification.domain.DeliveryStatus;
import com.electrahub.notification.domain.NotificationMessage;
import com.electrahub.notification.repository.NotificationMessageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class SensitiveNotificationAuditService {
    private final NotificationMessageRepository repository;
    private final ObjectMapper objectMapper;
    private final int maxAttempts;

    public SensitiveNotificationAuditService(
            NotificationMessageRepository repository,
            ObjectMapper objectMapper,
            @org.springframework.beans.factory.annotation.Value("${notification.sensitive-email.max-attempts:4}") int maxAttempts
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.maxAttempts = maxAttempts;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Reservation reserve(NotificationDtos.DomainNotificationEvent event) {
        String key = event.eventId() + ":" + event.eventType();
        NotificationMessage existing = repository
                .findByIdempotencyKeyAndChannelAndRecipientRef(key, Channel.EMAIL, event.recipientRef())
                .orElse(null);
        if (existing != null) {
            boolean retryable = existing.getStatus() == DeliveryStatus.FAILED
                    && existing.getLastError() != null
                    && existing.getLastError().startsWith("retryable:")
                    && existing.getAttempts() < maxAttempts;
            return new Reservation(existing.getId(), retryable);
        }

        NotificationMessage audit = new NotificationMessage(
                event.tenantId(), event.eventId(), key, event.recipientRef(), Channel.EMAIL, "user-email-otp");
        audit.setSubject("Verify your ElectraHub email");
        audit.setBody("[SENSITIVE CONTENT REDACTED]");
        audit.setPayloadJson(redactedPayload(event));
        return new Reservation(repository.saveAndFlush(audit).getId(), true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID notificationId, DispatchResult result) {
        NotificationMessage audit = repository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Sensitive notification audit not found"));
        if (result.success()) {
            audit.markDispatched(result.provider(), result.providerMessageId());
        } else if (result.skipped()) {
            audit.markSkipped(result.error());
        } else if (result.retryable()) {
            audit.markFailed("retryable: " + result.error());
        } else {
            audit.markFailed(result.error());
        }
        repository.save(audit);
    }

    private String redactedPayload(NotificationDtos.DomainNotificationEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", event.eventType());
        payload.put("eventId", event.eventId());
        payload.put("redacted", true);
        if (event.payload() != null && event.payload().get("challengeId") != null) {
            payload.put("challengeId", String.valueOf(event.payload().get("challengeId")));
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{\"redacted\":true}";
        }
    }

    public record Reservation(UUID notificationId, boolean shouldDispatch) {
    }
}
