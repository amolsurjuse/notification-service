package com.electrahub.notification.service;

import com.electrahub.notification.domain.Channel;
import com.electrahub.notification.domain.ContactStatus;
import com.electrahub.notification.domain.ContactType;
import com.electrahub.notification.domain.NotificationMessage;
import com.electrahub.notification.domain.PushDeviceRegistration;
import com.electrahub.notification.domain.UserContact;
import com.electrahub.notification.repository.NotificationMessageRepository;
import com.electrahub.notification.repository.PushDeviceRegistrationRepository;
import com.electrahub.notification.repository.UserContactRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class NotificationOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(NotificationOrchestrator.class);

    private final NotificationMessageRepository notificationRepository;
    private final UserContactRepository contactRepository;
    private final PushDeviceRegistrationRepository pushDeviceRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final PrivacyHashService privacyHashService;
    private final String exchange;
    private final String dispatchRoutingKey;

    public NotificationOrchestrator(
            NotificationMessageRepository notificationRepository,
            UserContactRepository contactRepository,
            PushDeviceRegistrationRepository pushDeviceRepository,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            PrivacyHashService privacyHashService,
            @Value("${notification.broker.exchange}") String exchange,
            @Value("${notification.broker.dispatch-routing-key}") String dispatchRoutingKey
    ) {
        this.notificationRepository = notificationRepository;
        this.contactRepository = contactRepository;
        this.pushDeviceRepository = pushDeviceRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.privacyHashService = privacyHashService;
        this.exchange = exchange;
        this.dispatchRoutingKey = dispatchRoutingKey;
    }

    @Transactional
    public List<NotificationDtos.NotificationResponse> submit(NotificationDtos.SubmitNotificationRequest request) {
        String idempotencyKey = normalizeIdempotencyKey(request);
        List<NotificationDtos.NotificationResponse> responses = new ArrayList<>();

        for (Channel channel : request.channels().stream().filter(Objects::nonNull).distinct().toList()) {
            if (channel == Channel.PUSH) {
                responses.addAll(createPushMessages(request, idempotencyKey));
            } else {
                NotificationMessage message = notificationRepository
                        .findByIdempotencyKeyAndChannelAndRecipientRef(idempotencyKey, channel, request.recipientRef())
                        .orElseGet(() -> createMessage(request, idempotencyKey, channel, request.recipientRef()));
                responses.add(NotificationMapper.toResponse(message));
            }
        }

        return responses;
    }

    @Transactional(readOnly = true)
    public List<NotificationDtos.NotificationResponse> inbox(String recipientRef) {
        return notificationRepository.findByRecipientRefOrderByCreatedAtDesc(recipientRef)
                .stream()
                .filter(message -> message.getChannel() == Channel.IN_APP)
                .map(NotificationMapper::toResponse)
                .toList();
    }

    @Transactional
    public NotificationDtos.NotificationResponse markRead(UUID id, String recipientRef) {
        NotificationMessage message = notificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        if (!message.getRecipientRef().equals(recipientRef)) {
            throw new IllegalArgumentException("Notification does not belong to recipient");
        }
        message.markRead();
        return NotificationMapper.toResponse(notificationRepository.save(message));
    }

    @Transactional
    public NotificationDtos.ContactResponse registerContact(NotificationDtos.ContactRegistrationRequest request) {
        String normalizedDestination = request.destination().trim();
        String hash = privacyHashService.sha256(normalizedDestination.toLowerCase(Locale.ROOT));
        String masked = switch (request.contactType()) {
            case EMAIL -> privacyHashService.maskEmail(normalizedDestination);
            case SMS -> privacyHashService.maskPhone(normalizedDestination);
            case PUSH -> privacyHashService.maskToken(normalizedDestination);
        };

        UserContact contact = contactRepository
                .findByTenantIdAndUserIdAndContactTypeAndDestinationHash(request.tenantId(), request.userId(), request.contactType(), hash)
                .orElseGet(() -> new UserContact(request.tenantId(), request.userId(), request.contactType(), hash, masked));
        contact.setProvider(trimToNull(request.provider()));
        contact.setPlatform(trimToNull(request.platform()));
        contact.setStatus(ContactStatus.ACTIVE);
        return NotificationMapper.toResponse(contactRepository.save(contact));
    }

    @Transactional
    public NotificationDtos.PushDeviceResponse registerPushDevice(NotificationDtos.PushDeviceRegistrationRequest request) {
        String tenantId = requiredTrim(request.tenantId(), "tenantId");
        String userId = requiredTrim(request.userId(), "userId");
        String provider = defaultProvider(request.provider());
        String deviceId = requiredTrim(request.deviceId(), "deviceId");
        String platform = requiredTrim(request.platform(), "platform").toLowerCase(Locale.ROOT);
        String token = requiredTrim(request.fcmToken(), "fcmToken");
        String tokenHash = privacyHashService.sha256(token);

        PushDeviceRegistration device = pushDeviceRepository
                .findByTenantIdAndUserIdAndProviderAndDeviceId(tenantId, userId, provider, deviceId)
                .orElseGet(() -> new PushDeviceRegistration(tenantId, userId, deviceId, platform, provider));
        device.updateToken(token, tokenHash, privacyHashService.maskToken(token));
        return NotificationMapper.toResponse(pushDeviceRepository.save(device));
    }

    @Transactional
    public void unregisterPushDevice(String tenantId, String userId, String provider, String deviceId) {
        pushDeviceRepository
                .findByTenantIdAndUserIdAndProviderAndDeviceId(
                        requiredTrim(tenantId, "tenantId"),
                        requiredTrim(userId, "userId"),
                        defaultProvider(provider),
                        requiredTrim(deviceId, "deviceId")
                )
                .ifPresent(device -> {
                    device.deactivate();
                    pushDeviceRepository.save(device);
                });
    }

    @Transactional
    public NotificationDtos.AcceptedResponse submitContact(NotificationDtos.ContactSubmissionRequest request, String ipAddress) {
        String eventId = "contact-" + privacyHashService.sha256(request.email().toLowerCase(Locale.ROOT) + ":" + request.message());
        Map<String, Object> payload = Map.of(
                "name", request.name(),
                "emailHash", privacyHashService.sha256(request.email().toLowerCase(Locale.ROOT)),
                "emailMasked", privacyHashService.maskEmail(request.email()),
                "company", defaultString(request.company()),
                "interest", defaultString(request.interest()),
                "message", request.message(),
                "ipHash", privacyHashService.sha256(defaultString(ipAddress))
        );
        NotificationDtos.SubmitNotificationRequest submit = new NotificationDtos.SubmitNotificationRequest(
                "electrahub",
                eventId,
                eventId,
                "support@electrahub.net",
                List.of(Channel.IN_APP, Channel.EMAIL),
                "contact-form-submission",
                "New ElectraHub contact request",
                request.message(),
                payload
        );
        return new NotificationDtos.AcceptedResponse("ACCEPTED", "Contact request accepted for async processing", submit(submit));
    }

    private List<NotificationDtos.NotificationResponse> createPushMessages(NotificationDtos.SubmitNotificationRequest request, String idempotencyKey) {
        List<PushDeviceRegistration> devices = pushDeviceRepository.findByTenantIdAndUserIdAndProviderAndStatus(
                request.tenantId(),
                request.recipientRef(),
                "firebase",
                ContactStatus.ACTIVE
        );
        if (devices.isEmpty()) {
            NotificationMessage message = notificationRepository
                    .findByIdempotencyKeyAndChannelAndRecipientRef(idempotencyKey, Channel.PUSH, request.recipientRef())
                    .orElseGet(() -> createMessage(request, idempotencyKey, Channel.PUSH, request.recipientRef()));
            return List.of(NotificationMapper.toResponse(message));
        }

        List<NotificationDtos.NotificationResponse> responses = new ArrayList<>();
        for (PushDeviceRegistration device : devices) {
            String childKey = childPushIdempotencyKey(idempotencyKey, device.getDeviceId());
            NotificationMessage message = notificationRepository
                    .findByIdempotencyKeyAndChannelAndRecipientRef(childKey, Channel.PUSH, device.getDeviceId())
                    .orElseGet(() -> createMessage(request, childKey, Channel.PUSH, device.getDeviceId()));
            responses.add(NotificationMapper.toResponse(message));
        }
        return responses;
    }

    private String childPushIdempotencyKey(String idempotencyKey, String deviceId) {
        String suffix = ":push:" + privacyHashService.sha256(deviceId).substring(0, 16);
        int maxPrefixLength = 160 - suffix.length();
        String prefix = idempotencyKey.length() > maxPrefixLength ? idempotencyKey.substring(0, maxPrefixLength) : idempotencyKey;
        return prefix + suffix;
    }

    private NotificationMessage createMessage(NotificationDtos.SubmitNotificationRequest request, String idempotencyKey, Channel channel, String recipientRef) {
        NotificationMessage message = new NotificationMessage(
                request.tenantId(),
                request.eventId(),
                idempotencyKey,
                recipientRef,
                channel,
                request.templateId()
        );
        message.setSubject(trimToNull(request.subject()));
        message.setBody(trimToNull(request.body()));
        message.setPayloadJson(toJson(request.payload()));
        NotificationMessage saved = notificationRepository.save(message);
        publishDispatch(saved.getId());
        return saved;
    }

    private void publishDispatch(UUID notificationId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            sendDispatch(notificationId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sendDispatch(notificationId);
            }
        });
    }

    private void sendDispatch(UUID notificationId) {
        rabbitTemplate.convertAndSend(exchange, dispatchRoutingKey, new DispatchCommand(notificationId));
        log.info("Queued notification {} for async dispatch", notificationId);
    }

    private String normalizeIdempotencyKey(NotificationDtos.SubmitNotificationRequest request) {
        String requested = trimToNull(request.idempotencyKey());
        if (requested != null) {
            return requested;
        }
        return request.eventId() + ":" + request.templateId();
    }

    private String toJson(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Notification payload could not be serialized", ex);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String requiredTrim(String value, String field) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return trimmed;
    }

    private String defaultProvider(String provider) {
        String trimmed = trimToNull(provider);
        return trimmed == null ? "firebase" : trimmed.toLowerCase(Locale.ROOT);
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}
