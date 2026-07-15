package com.electrahub.notification.service;

import com.electrahub.notification.domain.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

            for (Channel channel : channels) {
                String recipientRef = recipientFor(event, channel);
                if (recipientRef == null) {
                    log.warn("Ignoring {} channel for event {} because no recipient is available", channel, event.eventType());
                    continue;
                }
                NotificationDtos.SubmitNotificationRequest request = new NotificationDtos.SubmitNotificationRequest(
                        event.tenantId(),
                        event.eventId(),
                        idempotencyKeyFor(event, payload),
                        recipientRef,
                        List.of(channel),
                        templateFor(event.eventType()),
                        subjectFor(event.eventType(), payload),
                        bodyFor(event.eventType(), payload),
                        payload
                );
                orchestrator.submit(request);
            }
        });
    }

    private List<Channel> channelsFor(String eventType) {
        return switch (eventType) {
            case "USER_ACCOUNT_CREATED", "USER_PASSWORD_CHANGED" -> List.of(Channel.EMAIL, Channel.PUSH, Channel.IN_APP);
            case "USER_PASSWORD_RESET_REQUESTED", "USER_EMAIL_VERIFICATION_REQUESTED" -> List.of(Channel.EMAIL);
            case "CHARGING_SESSION_STARTED", "CHARGING_SESSION_STOPPED", "CHARGING_SESSION_START_TIMEOUT", "CHARGING_BATTERY_FULL",
                 "CHARGING_IDLE_WARNING", "CHARGING_IDLE_STARTED", "CHARGING_LOW_BALANCE_STOP" -> List.of(Channel.PUSH, Channel.IN_APP);
            case "PAYMENT_RECEIPT_READY", "CHARGING_RECEIPT_READY" -> List.of(Channel.EMAIL, Channel.PUSH, Channel.IN_APP);
            case "PAYMENT_CARD_ADDED", "PAYMENT_CARD_REMOVED",
                 "PAYMENT_AUTO_TOP_UP_ENABLED", "PAYMENT_AUTO_TOP_UP_DISABLED", "PAYMENT_AUTO_TOP_UP_UPDATED",
                 "PAYMENT_AUTO_TOP_UP_COMPLETED", "PAYMENT_WALLET_TOP_UP_COMPLETED", "USER_PROFILE_UPDATED" -> List.of(Channel.IN_APP);
            case "SUPPORT_CONTACT_CREATED", "SUPPORT_ESCALATION_CREATED" -> List.of(Channel.EMAIL);
            default -> List.of();
        };
    }

    private String recipientFor(NotificationDtos.DomainNotificationEvent event, Channel channel) {
        if (channel == Channel.PUSH || channel == Channel.IN_APP) {
            String userId = text(event.userId());
            if (userId != null) {
                return userId;
            }
        }
        return text(event.recipientRef());
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
            case "CHARGING_SESSION_START_TIMEOUT" -> "Charging could not start";
            case "CHARGING_BATTERY_FULL" -> "Battery full";
            case "CHARGING_IDLE_WARNING" -> "Idle fees may start soon";
            case "CHARGING_IDLE_STARTED" -> "Idle period started";
            case "CHARGING_LOW_BALANCE_STOP" -> "Charging stopped due to low balance";
            case "PAYMENT_RECEIPT_READY", "CHARGING_RECEIPT_READY" -> "Your ElectraHub receipt";
            case "PAYMENT_CARD_ADDED" -> "Credit card added";
            case "PAYMENT_CARD_REMOVED" -> "Credit card removed";
            case "PAYMENT_AUTO_TOP_UP_ENABLED" -> "Auto top-up enabled";
            case "PAYMENT_AUTO_TOP_UP_DISABLED" -> "Auto top-up disabled";
            case "PAYMENT_AUTO_TOP_UP_UPDATED" -> "Auto top-up updated";
            case "PAYMENT_AUTO_TOP_UP_COMPLETED" -> "Wallet topped up automatically";
            case "PAYMENT_WALLET_TOP_UP_COMPLETED" -> "Wallet top-up complete";
            case "USER_PROFILE_UPDATED" -> "Profile updated";
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
            case "CHARGING_SESSION_START_TIMEOUT" -> "The charger did not start in time. Please try another connector or contact support.";
            case "CHARGING_BATTERY_FULL" -> "Your vehicle battery is full.";
            case "CHARGING_IDLE_WARNING" -> "Please move your vehicle soon to avoid idle fees.";
            case "CHARGING_IDLE_STARTED" -> "Your idle period has started.";
            case "CHARGING_LOW_BALANCE_STOP" -> "Your charging session was stopped because the available balance reached the configured minimum. Add funds before starting another session.";
            case "PAYMENT_RECEIPT_READY", "CHARGING_RECEIPT_READY" -> "Your receipt is ready.";
            case "PAYMENT_CARD_ADDED" -> cardDescription(payload) + " was added to your payment methods.";
            case "PAYMENT_CARD_REMOVED" -> cardDescription(payload) + " was removed from your payment methods.";
            case "PAYMENT_AUTO_TOP_UP_ENABLED" -> "Auto top-up is on. "
                    + amount(payload, "amount") + " will be added when your wallet drops below "
                    + amount(payload, "threshold") + ", using " + fundingCardDescription(payload) + ".";
            case "PAYMENT_AUTO_TOP_UP_DISABLED" -> "Auto top-up is off. Your wallet will no longer be funded automatically.";
            case "PAYMENT_AUTO_TOP_UP_UPDATED" -> "Auto top-up settings were updated. Amount: "
                    + amount(payload, "amount") + "; threshold: " + amount(payload, "threshold")
                    + "; card: " + fundingCardDescription(payload) + ".";
            case "PAYMENT_AUTO_TOP_UP_COMPLETED" -> amount(payload, "amount")
                    + " was added automatically. New wallet balance: " + amount(payload, "newBalance") + ".";
            case "PAYMENT_WALLET_TOP_UP_COMPLETED" -> amount(payload, "amount")
                    + " was added to your wallet. New balance: " + amount(payload, "newBalance") + ".";
            case "USER_PROFILE_UPDATED" -> "Your profile details were updated.";
            default -> String.valueOf(payload.getOrDefault("message", subjectFor(eventType, payload)));
        };
    }

    private String cardDescription(Map<String, Object> payload) {
        return String.valueOf(payload.getOrDefault("brand", "Card"))
                + " ending in " + String.valueOf(payload.getOrDefault("last4", "****"));
    }

    private String fundingCardDescription(Map<String, Object> payload) {
        return String.valueOf(payload.getOrDefault("cardBrand", "card"))
                + " ending in " + String.valueOf(payload.getOrDefault("cardLast4", "****"));
    }

    private String amount(Map<String, Object> payload, String field) {
        return String.valueOf(payload.getOrDefault(field, "0.00")) + " "
                + String.valueOf(payload.getOrDefault("currency", "USD"));
    }

    static String idempotencyKeyFor(NotificationDtos.DomainNotificationEvent event, Map<String, Object> payload) {
        String eventType = event.eventType() == null ? "" : event.eventType().trim().toUpperCase(Locale.ROOT);
        String sessionId = text(payload == null ? null : payload.get("sessionId"));
        if (eventType.startsWith("CHARGING_") && sessionId != null) {
            String seed = String.join(":", textOrEmpty(event.tenantId()), eventType, sessionId);
            return "charging:" + UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
        }
        return event.eventId() + ":" + event.eventType();
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String result = String.valueOf(value).trim();
        return result.isEmpty() ? null : result;
    }

    private static String textOrEmpty(Object value) {
        String result = text(value);
        return result == null ? "" : result;
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
