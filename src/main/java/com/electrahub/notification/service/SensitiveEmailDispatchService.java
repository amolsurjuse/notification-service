package com.electrahub.notification.service;

import com.electrahub.notification.domain.Channel;
import com.electrahub.notification.domain.NotificationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class SensitiveEmailDispatchService {
    private static final Logger log = LoggerFactory.getLogger(SensitiveEmailDispatchService.class);

    private final SensitiveNotificationAuditService auditService;
    private final Map<Channel, ChannelAdapter> adapters = new EnumMap<>(Channel.class);

    public SensitiveEmailDispatchService(SensitiveNotificationAuditService auditService, List<ChannelAdapter> adapters) {
        this.auditService = auditService;
        adapters.forEach(adapter -> this.adapters.put(adapter.channel(), adapter));
    }

    public void dispatchEmailOtp(NotificationDtos.DomainNotificationEvent event) {
        String code = value(event.payload(), "code");
        if (code == null || !code.matches("\\d{6}")) {
            throw new IllegalArgumentException("Email OTP event is missing a valid code");
        }
        SensitiveNotificationAuditService.Reservation reservation = auditService.reserve(event);
        if (!reservation.shouldDispatch()) {
            log.info("Sensitive email event {} has already been handled", event.eventId());
            return;
        }

        ChannelAdapter adapter = adapters.get(Channel.EMAIL);
        DispatchResult result;
        if (adapter == null) {
            result = DispatchResult.failure("notification-service", "No email adapter is configured");
        } else {
            NotificationMessage transientMessage = new NotificationMessage(
                    event.tenantId(), event.eventId(), event.eventId() + ":" + event.eventType(),
                    event.recipientRef(), Channel.EMAIL, "user-email-otp");
            transientMessage.setSubject("Verify your ElectraHub email");
            transientMessage.setBody("Your ElectraHub verification code is " + code
                    + ". It expires in " + valueOrDefault(event.payload(), "expiresInMinutes", "10")
                    + " minutes. If you did not request this code, you can ignore this email.");
            transientMessage.setPayloadJson("{\"sensitive\":true}");
            try {
                result = adapter.dispatch(transientMessage);
            } catch (RuntimeException ex) {
                result = DispatchResult.retryableFailure("notification-service", "adapter exception: " + ex.getMessage());
            }
        }

        auditService.complete(reservation.notificationId(), result);
        if (result.retryable()) {
            throw new RetryableNotificationDispatchException(reservation.notificationId(), result.error());
        }
        if (!result.success()) {
            log.warn("Sensitive email event {} was not delivered: {}", event.eventId(), result.error());
        }
    }

    private String value(Map<String, Object> payload, String key) {
        if (payload == null || payload.get(key) == null) {
            return null;
        }
        String value = String.valueOf(payload.get(key)).trim();
        return value.isEmpty() ? null : value;
    }

    private String valueOrDefault(Map<String, Object> payload, String key, String fallback) {
        String value = value(payload, key);
        return value == null ? fallback : value;
    }
}
