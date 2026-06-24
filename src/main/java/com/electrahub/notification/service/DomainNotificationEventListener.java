package com.electrahub.notification.service;

import com.electrahub.notification.domain.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DomainNotificationEventListener {
    private static final Logger log = LoggerFactory.getLogger(DomainNotificationEventListener.class);

    private final NotificationOrchestrator orchestrator;
    private final String driverResetPasswordUrl;

    public DomainNotificationEventListener(
            NotificationOrchestrator orchestrator,
            @Value("${notification.portal.driver-reset-password-url}") String driverResetPasswordUrl
    ) {
        this.orchestrator = orchestrator;
        this.driverResetPasswordUrl = driverResetPasswordUrl;
    }

    @RabbitListener(queues = "${notification.broker.domain-event-queue}")
    public void onDomainEvent(NotificationDtos.DomainNotificationEvent event) {
        withEventTrace(event.eventId(), () -> {
            List<Channel> channels = channelsFor(event.eventType());
            if (channels.isEmpty()) {
                log.info("Ignoring notification domain event {} because no channel is configured", event.eventType());
                return;
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            if (event.payload() != null) {
                payload.putAll(event.payload());
            }
            payload.put("eventType", event.eventType());
            payload.put("eventId", event.eventId());
            payload.put("userId", event.userId() == null ? "" : event.userId());
            if ("USER_PASSWORD_RESET_REQUESTED".equals(event.eventType()) && payload.get("resetUrl") == null) {
                String token = String.valueOf(payload.getOrDefault("resetToken", ""));
                payload.put("resetUrl", driverResetPasswordUrl + "?token=" + token);
            }

            NotificationDtos.SubmitNotificationRequest request = new NotificationDtos.SubmitNotificationRequest(
                    event.tenantId(),
                    event.eventId(),
                    event.eventId() + ":" + event.eventType(),
                    event.recipientRef(),
                    channels,
                    templateFor(event.eventType()),
                    subjectFor(event.eventType(), payload),
                    bodyFor(event.eventType(), payload),
                    payload
            );
            orchestrator.submit(request);
        });
    }

    private List<Channel> channelsFor(String eventType) {
        return switch (eventType) {
            case "USER_ACCOUNT_CREATED", "USER_PASSWORD_CHANGED" -> List.of(Channel.EMAIL, Channel.PUSH);
            case "USER_PASSWORD_RESET_REQUESTED", "USER_EMAIL_VERIFICATION_REQUESTED" -> List.of(Channel.EMAIL);
            case "CHARGING_SESSION_STARTED", "CHARGING_SESSION_STOPPED", "CHARGING_BATTERY_FULL",
                 "CHARGING_IDLE_WARNING", "CHARGING_IDLE_STARTED" -> List.of(Channel.PUSH);
            case "PAYMENT_RECEIPT_READY", "CHARGING_RECEIPT_READY" -> List.of(Channel.EMAIL);
            case "SUPPORT_CONTACT_CREATED", "SUPPORT_ESCALATION_CREATED" -> List.of(Channel.EMAIL);
            default -> List.of();
        };
    }

    private String templateFor(String eventType) {
        return eventType.toLowerCase().replace('_', '-');
    }

    private String subjectFor(String eventType, Map<String, Object> payload) {
        return switch (eventType) {
            case "USER_ACCOUNT_CREATED" -> "Welcome to ElectraHub";
            case "USER_PASSWORD_CHANGED" -> "Your ElectraHub password was changed";
            case "USER_PASSWORD_RESET_REQUESTED" -> "Reset your ElectraHub password";
            case "USER_EMAIL_VERIFICATION_REQUESTED" -> "Verify your ElectraHub email";
            case "CHARGING_SESSION_STARTED" -> "Charging session started";
            case "CHARGING_SESSION_STOPPED" -> "Charging session stopped";
            case "CHARGING_BATTERY_FULL" -> "Battery full";
            case "CHARGING_IDLE_WARNING" -> "Idle fees may start soon";
            case "CHARGING_IDLE_STARTED" -> "Idle period started";
            case "PAYMENT_RECEIPT_READY", "CHARGING_RECEIPT_READY" -> "Your ElectraHub receipt";
            case "SUPPORT_CONTACT_CREATED", "SUPPORT_ESCALATION_CREATED" -> "ElectraHub support request";
            default -> "ElectraHub notification";
        };
    }

    private String bodyFor(String eventType, Map<String, Object> payload) {
        return switch (eventType) {
            case "USER_PASSWORD_RESET_REQUESTED" -> "Use this link to reset your password: " + payload.getOrDefault("resetUrl", "");
            case "USER_EMAIL_VERIFICATION_REQUESTED" -> "Use this link to verify your email address: " + payload.getOrDefault("verificationUrl", "");
            case "USER_PASSWORD_CHANGED" -> "Your password was changed. If this was not you, contact support immediately.";
            case "CHARGING_SESSION_STARTED" -> "Your charging session has started.";
            case "CHARGING_SESSION_STOPPED" -> "Your charging session has stopped.";
            case "CHARGING_BATTERY_FULL" -> "Your vehicle battery is full.";
            case "CHARGING_IDLE_WARNING" -> "Please move your vehicle soon to avoid idle fees.";
            case "CHARGING_IDLE_STARTED" -> "Your idle period has started.";
            case "PAYMENT_RECEIPT_READY", "CHARGING_RECEIPT_READY" -> "Your receipt is ready.";
            default -> String.valueOf(payload.getOrDefault("message", subjectFor(eventType, payload)));
        };
    }

    private void withEventTrace(String eventId, Runnable action) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        try {
            MDC.put("traceId", traceIdFrom(eventId));
            MDC.put("spanId", UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            action.run();
        } finally {
            if (previous == null || previous.isEmpty()) {
                MDC.clear();
            } else {
                MDC.setContextMap(previous);
            }
        }
    }

    private String traceIdFrom(String seed) {
        String value = seed == null || seed.isBlank() ? UUID.randomUUID().toString() : seed;
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
    }
}
