package com.electrahub.notification.service;

import com.electrahub.notification.domain.Channel;
import com.electrahub.notification.domain.ContactStatus;
import com.electrahub.notification.domain.DeliveryStatus;
import com.electrahub.notification.domain.NotificationMessage;
import com.electrahub.notification.repository.NotificationMessageRepository;
import com.electrahub.notification.repository.PushDeviceRegistrationRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class FirebasePushAdapter implements ChannelAdapter {
    private static final Logger log = LoggerFactory.getLogger(FirebasePushAdapter.class);
    private static final Duration QUOTA_WINDOW = Duration.ofHours(24);
    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {};

    private final NotificationMessageRepository notificationRepository;
    private final PushDeviceRegistrationRepository pushDeviceRepository;
    private final ObjectMapper objectMapper;
    private final FirebasePushSender sender;
    private final Clock clock;
    private final boolean enabled;
    private final int dailyLimit;
    private final int ratePerSecond;
    private Instant nextAllowedSendAt;

    public FirebasePushAdapter(
            NotificationMessageRepository notificationRepository,
            PushDeviceRegistrationRepository pushDeviceRepository,
            ObjectMapper objectMapper,
            FirebasePushSender sender,
            Clock clock,
            boolean enabled,
            int dailyLimit,
            int ratePerSecond
    ) {
        this.notificationRepository = notificationRepository;
        this.pushDeviceRepository = pushDeviceRepository;
        this.objectMapper = objectMapper;
        this.sender = sender;
        this.clock = clock;
        this.enabled = enabled;
        this.dailyLimit = Math.max(0, dailyLimit);
        this.ratePerSecond = Math.max(1, ratePerSecond);
        this.nextAllowedSendAt = Instant.EPOCH;
    }

    @Override
    public Channel channel() {
        return Channel.PUSH;
    }

    @Override
    public DispatchResult dispatch(NotificationMessage message) {
        if (!enabled) {
            return DispatchResult.failure("firebase-fcm", "PUSH_DISABLED");
        }
        if (!sender.configured()) {
            return DispatchResult.retryableFailure("firebase-fcm", "FIREBASE_NOT_CONFIGURED");
        }

        OffsetDateTime since = OffsetDateTime.now(clock).minus(QUOTA_WINDOW);
        long dispatchedInWindow = notificationRepository.countDispatchedSince(
                Channel.PUSH,
                DeliveryStatus.DISPATCHED,
                since
        );
        if (dispatchedInWindow >= dailyLimit) {
            return DispatchResult.failure("firebase-fcm", "PUSH_DAILY_QUOTA_EXCEEDED rollingWindowHours=24 limit=" + dailyLimit);
        }

        Map<String, Object> payload = parsePayload(message.getPayloadJson());
        Optional<com.electrahub.notification.domain.PushDeviceRegistration> registeredDevice = Optional.empty();
        String token = firstText(payload, "fcmToken", "deviceToken", "pushToken", "registrationToken");
        if (token == null) {
            registeredDevice = pushDeviceRepository
                    .findFirstByTenantIdAndProviderAndDeviceIdAndStatusOrderByLastSeenAtDesc(
                            message.getTenantId(),
                            "firebase",
                            message.getRecipientRef(),
                            ContactStatus.ACTIVE
                    );
            token = registeredDevice.map(device -> safeTrim(device.getFcmToken())).orElse(null);
        }
        if (token == null && looksLikeLegacyToken(message.getRecipientRef())) {
            token = safeTrim(message.getRecipientRef());
        }
        if (token == null) {
            return DispatchResult.skipped("firebase-fcm", "PUSH_TOKEN_REQUIRED");
        }

        throttle();
        try {
            String providerMessageId = sender.send(toFirebaseMessage(message, payload, token));
            return DispatchResult.success("firebase-fcm", providerMessageId);
        } catch (FirebaseMessagingException ex) {
            MessagingErrorCode errorCode = ex.getMessagingErrorCode();
            String error = "FIREBASE_SEND_FAILED code=" + errorCode + " message=" + ex.getMessage();
            if (isInvalidRegistration(errorCode)) {
                deactivateInvalidDevice(registeredDevice);
                log.info("Firebase token rejected for notification {} device {}; registration deactivated", message.getId(), message.getRecipientRef());
                return DispatchResult.failure("firebase-fcm", error);
            }
            log.warn("Retryable Firebase provider failure for notification {}: {}", message.getId(), error);
            return DispatchResult.retryableFailure("firebase-fcm", error);
        } catch (Exception ex) {
            log.warn("Retryable Firebase provider failure for notification {}: {}", message.getId(), ex.getMessage());
            return DispatchResult.retryableFailure("firebase-fcm", "FIREBASE_SEND_FAILED: " + ex.getMessage());
        }
    }

    private boolean isInvalidRegistration(MessagingErrorCode errorCode) {
        return errorCode == MessagingErrorCode.INVALID_ARGUMENT
                || errorCode == MessagingErrorCode.SENDER_ID_MISMATCH
                || errorCode == MessagingErrorCode.UNREGISTERED;
    }

    private void deactivateInvalidDevice(
            Optional<com.electrahub.notification.domain.PushDeviceRegistration> registeredDevice
    ) {
        if (registeredDevice.isEmpty()) {
            return;
        }
        com.electrahub.notification.domain.PushDeviceRegistration device = registeredDevice.get();
        device.deactivate();
        pushDeviceRepository.save(device);
    }

    private Message toFirebaseMessage(NotificationMessage message, Map<String, Object> payload, String token) {
        Message.Builder builder = Message.builder()
                .setToken(token)
                .putAllData(dataPayload(message, payload));

        String title = firstText(payload, "title");
        if (title == null) {
            title = safeTrim(message.getSubject());
        }
        String body = firstText(payload, "body", "message");
        if (body == null) {
            body = safeTrim(message.getBody());
        }
        if (title != null || body != null) {
            builder.setNotification(Notification.builder()
                    .setTitle(title == null ? "" : title)
                    .setBody(body == null ? "" : body)
                    .build());
        }
        return builder.build();
    }

    private Map<String, String> dataPayload(NotificationMessage message, Map<String, Object> payload) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("notificationId", message.getId().toString());
        data.put("tenantId", message.getTenantId());
        data.put("eventId", message.getEventId());
        data.put("templateId", message.getTemplateId());
        payload.forEach((key, value) -> {
            if (value == null || key.equals("fcmToken") || key.equals("deviceToken") || key.equals("pushToken") || key.equals("registrationToken")) {
                return;
            }
            data.put(key, String.valueOf(value));
        });
        return data;
    }

    private Map<String, Object> parsePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payloadJson, PAYLOAD_TYPE);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String firstText(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            String value = safeTrim(payload.get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String safeTrim(Object value) {
        if (value == null) {
            return null;
        }
        String trimmed = String.valueOf(value).trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean looksLikeLegacyToken(String recipientRef) {
        String value = safeTrim(recipientRef);
        return value != null && value.length() >= 80 && !value.contains("@");
    }

    private synchronized void throttle() {
        Instant now = clock.instant();
        if (now.isBefore(nextAllowedSendAt)) {
            sleepUntil(nextAllowedSendAt, now);
        }
        long spacingMillis = Math.max(1000L / ratePerSecond, 1L);
        nextAllowedSendAt = clock.instant().plusMillis(spacingMillis);
    }

    private void sleepUntil(Instant target, Instant now) {
        long millis = Duration.between(now, target).toMillis();
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
