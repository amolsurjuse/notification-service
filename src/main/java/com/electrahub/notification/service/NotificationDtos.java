package com.electrahub.notification.service;

import com.electrahub.notification.domain.Channel;
import com.electrahub.notification.domain.ContactType;
import com.electrahub.notification.domain.DeliveryStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class NotificationDtos {
    private NotificationDtos() {
    }

    public record SubmitNotificationRequest(
            @NotBlank String tenantId,
            @NotBlank String eventId,
            String idempotencyKey,
            @NotBlank String recipientRef,
            @NotEmpty List<Channel> channels,
            @NotBlank String templateId,
            @Size(max = 240) String subject,
            @Size(max = 2000) String body,
            Map<String, Object> payload
    ) {
    }

    public record NotificationResponse(
            UUID id,
            String tenantId,
            String eventId,
            String recipientRef,
            Channel channel,
            String templateId,
            String subject,
            DeliveryStatus status,
            OffsetDateTime createdAt,
            OffsetDateTime dispatchedAt,
            OffsetDateTime readAt
    ) {
    }

    public record ContactRegistrationRequest(
            @NotBlank String tenantId,
            @NotBlank String userId,
            @NotNull ContactType contactType,
            @NotBlank String destination,
            String provider,
            String platform
    ) {
    }

    public record ContactResponse(
            UUID id,
            String tenantId,
            String userId,
            ContactType contactType,
            String maskedDestination,
            String provider,
            String platform,
            String status
    ) {
    }

    public record PushDeviceRegistrationRequest(
            @NotBlank String tenantId,
            @NotBlank String userId,
            @NotBlank @Size(max = 160) String deviceId,
            @NotBlank @Size(max = 32) String platform,
            @NotBlank @Size(max = 512) String fcmToken,
            @Size(max = 64) String provider
    ) {
    }

    public record PushDeviceResponse(
            UUID id,
            String tenantId,
            String userId,
            String deviceId,
            String platform,
            String provider,
            String maskedToken,
            String status,
            OffsetDateTime lastSeenAt
    ) {
    }

    public record ContactSubmissionRequest(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Email @Size(max = 240) String email,
            @Size(max = 160) String company,
            @Size(max = 120) String interest,
            @NotBlank @Size(max = 2000) String message
    ) {
    }

    public record AcceptedResponse(String status, String message, List<NotificationResponse> notifications) {
    }

    public record DomainNotificationEvent(
            @NotBlank String eventId,
            @NotBlank String eventType,
            @NotBlank String tenantId,
            @NotBlank String recipientRef,
            String userId,
            String occurredAt,
            Map<String, Object> payload
    ) {
    }
}
