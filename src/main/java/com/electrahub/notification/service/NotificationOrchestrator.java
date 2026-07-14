package com.electrahub.notification.service;

import com.electrahub.notification.domain.Channel;
import com.electrahub.notification.domain.ContactStatus;
import com.electrahub.notification.domain.ContactType;
import com.electrahub.notification.domain.DeliveryStatus;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
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
    private final InboxRealtimePublisher inboxRealtimePublisher;
    private final String exchange;
    private final String dispatchRoutingKey;
    private final List<String> contactRecipients;

    public NotificationOrchestrator(
            NotificationMessageRepository notificationRepository,
            UserContactRepository contactRepository,
            PushDeviceRegistrationRepository pushDeviceRepository,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            PrivacyHashService privacyHashService,
            InboxRealtimePublisher inboxRealtimePublisher,
            @Value("${notification.broker.exchange}") String exchange,
            @Value("${notification.broker.dispatch-routing-key}") String dispatchRoutingKey,
            @Value("${notification.contact.recipients:support@electrahub.net}") String contactRecipients
    ) {
        this.notificationRepository = notificationRepository;
        this.contactRepository = contactRepository;
        this.pushDeviceRepository = pushDeviceRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.privacyHashService = privacyHashService;
        this.inboxRealtimePublisher = inboxRealtimePublisher;
        this.exchange = exchange;
        this.dispatchRoutingKey = dispatchRoutingKey;
        this.contactRecipients = parseRecipients(contactRecipients);
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

    @Transactional(readOnly = true)
    public NotificationDtos.InboxPageResponse inboxForUser(
            String authenticatedTenantId,
            String authenticatedUserId,
            NotificationDtos.InboxReadState state,
            int requestedPage,
            int requestedSize
    ) {
        String tenantId = requiredTrim(authenticatedTenantId, "authenticatedTenantId");
        String userId = requiredTrim(authenticatedUserId, "authenticatedUserId");
        int pageNumber = Math.max(0, requestedPage);
        int pageSize = Math.min(100, Math.max(1, requestedSize));
        PageRequest pageable = PageRequest.of(
                pageNumber,
                pageSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        var page = switch (state == null ? NotificationDtos.InboxReadState.ALL : state) {
            case ALL -> notificationRepository.findByTenantIdAndRecipientRefAndChannel(
                    tenantId, userId, Channel.IN_APP, pageable);
            case UNREAD -> notificationRepository.findByTenantIdAndRecipientRefAndChannelAndReadAtIsNull(
                    tenantId, userId, Channel.IN_APP, pageable);
            case READ -> notificationRepository.findByTenantIdAndRecipientRefAndChannelAndReadAtIsNotNull(
                    tenantId, userId, Channel.IN_APP, pageable);
        };
        return new NotificationDtos.InboxPageResponse(
                page.getContent().stream().map(NotificationMapper::toResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext(),
                unreadCount(tenantId, userId)
        );
    }

    @Transactional
    public NotificationDtos.NotificationResponse markRead(UUID id, String recipientRef) {
        NotificationMessage message = notificationRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Notification not found"));
        if (!message.getRecipientRef().equals(recipientRef)) {
            throw new IllegalArgumentException("Notification does not belong to recipient");
        }
        message.markRead();
        NotificationMessage saved = notificationRepository.save(message);
        inboxRealtimePublisher.updated(saved);
        return NotificationMapper.toResponse(saved);
    }

    @Transactional
    public NotificationDtos.NotificationResponse setInboxReadState(
            UUID id,
            String authenticatedTenantId,
            String authenticatedUserId,
            boolean read
    ) {
        String tenantId = requiredTrim(authenticatedTenantId, "authenticatedTenantId");
        String userId = requiredTrim(authenticatedUserId, "authenticatedUserId");
        NotificationMessage message = notificationRepository.findByIdAndTenantIdAndRecipientRefAndChannel(
                        id, tenantId, userId, Channel.IN_APP)
                .orElseThrow(() -> new NoSuchElementException("Notification not found"));
        if (read) {
            message.markRead();
        } else {
            message.markUnread();
        }
        NotificationMessage saved = notificationRepository.save(message);
        inboxRealtimePublisher.updated(saved);
        return NotificationMapper.toResponse(saved);
    }

    @Transactional
    public NotificationDtos.InboxMutationResponse markAllInboxRead(
            String authenticatedTenantId,
            String authenticatedUserId
    ) {
        String tenantId = requiredTrim(authenticatedTenantId, "authenticatedTenantId");
        String userId = requiredTrim(authenticatedUserId, "authenticatedUserId");
        int affected = notificationRepository.markAllInboxNotificationsRead(
                tenantId,
                userId,
                Channel.IN_APP,
                DeliveryStatus.READ,
                OffsetDateTime.now()
        );
        if (affected > 0) {
            inboxRealtimePublisher.readAll(tenantId, userId);
        }
        return new NotificationDtos.InboxMutationResponse(affected, unreadCount(tenantId, userId));
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
    public NotificationDtos.PushDeviceResponse registerPushDevice(
            String authenticatedTenantId,
            String authenticatedUserId,
            NotificationDtos.PushDeviceRegistrationRequest request
    ) {
        String tenantId = requiredTrim(authenticatedTenantId, "authenticatedTenantId");
        String userId = requiredTrim(authenticatedUserId, "authenticatedUserId");
        String provider = defaultProvider(request.provider());
        String deviceId = requiredTrim(request.deviceId(), "deviceId");
        String platform = requiredTrim(request.platform(), "platform").toLowerCase(Locale.ROOT);
        String token = requiredTrim(request.fcmToken(), "fcmToken");
        String tokenHash = privacyHashService.sha256(token);

        pushDeviceRepository.findAllByTenantIdAndProviderAndDeviceIdAndStatus(
                        tenantId,
                        provider,
                        deviceId,
                        ContactStatus.ACTIVE
                ).stream()
                .filter(existing -> !existing.getUserId().equals(userId))
                .forEach(existing -> {
                    existing.deactivate();
                    pushDeviceRepository.save(existing);
                });

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
        List<NotificationDtos.NotificationResponse> responses = submitForContactRecipients(
                eventId,
                "contact-form-submission",
                "New ElectraHub contact request",
                request.message(),
                payload
        );
        return new NotificationDtos.AcceptedResponse("ACCEPTED", "Contact request accepted for async processing", eventId, responses);
    }

    @Transactional
    public NotificationDtos.AcceptedResponse submitProjectBrief(NotificationDtos.ProjectBriefSubmissionRequest request, String ipAddress) {
        if (trimToNull(request.honeypot()) != null) {
            throw new IllegalArgumentException("Invalid project brief submission");
        }
        String normalizedEmail = request.email().toLowerCase(Locale.ROOT);
        String eventId = "project-brief-" + privacyHashService.sha256(normalizedEmail + ":" + request.brief());
        Map<String, Object> payload = Map.ofEntries(
                Map.entry("name", request.name()),
                Map.entry("emailHash", privacyHashService.sha256(normalizedEmail)),
                Map.entry("emailMasked", privacyHashService.maskEmail(request.email())),
                Map.entry("company", defaultString(request.company())),
                Map.entry("phone", defaultString(request.phone())),
                Map.entry("siteType", request.siteType()),
                Map.entry("brief", request.brief()),
                Map.entry("sourcePage", defaultString(request.sourcePage())),
                Map.entry("marketingConsent", Boolean.TRUE.equals(request.marketingConsent())),
                Map.entry("utmSource", defaultString(request.utmSource())),
                Map.entry("utmMedium", defaultString(request.utmMedium())),
                Map.entry("utmCampaign", defaultString(request.utmCampaign())),
                Map.entry("ipHash", privacyHashService.sha256(defaultString(ipAddress)))
        );
        String subject = "New ElectraHub project brief";
        String body = request.brief();
        List<NotificationDtos.NotificationResponse> responses = submitForContactRecipients(
                eventId,
                "project-brief-submission",
                subject,
                body,
                payload
        );
        return new NotificationDtos.AcceptedResponse(
                "ACCEPTED",
                "Thanks " + request.name() + " — we received your project brief and will follow up shortly.",
                eventId,
                responses
        );
    }

    private List<NotificationDtos.NotificationResponse> submitForContactRecipients(
            String eventId,
            String templateId,
            String subject,
            String body,
            Map<String, Object> payload
    ) {
        List<NotificationDtos.NotificationResponse> responses = new ArrayList<>();
        for (String recipient : contactRecipients) {
            NotificationDtos.SubmitNotificationRequest submit = new NotificationDtos.SubmitNotificationRequest(
                    "electrahub",
                    eventId,
                    eventId + ":" + recipient,
                    recipient,
                    List.of(Channel.IN_APP, Channel.EMAIL),
                    templateId,
                    subject,
                    body,
                    payload
            );
            responses.addAll(submit(submit));
        }
        return responses;
    }

    private List<NotificationDtos.NotificationResponse> createPushMessages(NotificationDtos.SubmitNotificationRequest request, String idempotencyKey) {
        List<PushDeviceRegistration> devices = pushDeviceRepository.findByTenantIdAndUserIdAndProviderAndStatus(
                request.tenantId(),
                request.recipientRef(),
                "firebase",
                ContactStatus.ACTIVE
        );
        if (devices.isEmpty()) {
            log.info("Push notification {} skipped for recipient {} because no active Firebase devices are registered", request.templateId(), request.recipientRef());
            NotificationMessage message = notificationRepository
                    .findByIdempotencyKeyAndChannelAndRecipientRef(idempotencyKey, Channel.PUSH, request.recipientRef())
                    .orElseGet(() -> createSkippedMessage(request, idempotencyKey, Channel.PUSH, request.recipientRef(), "NO_ACTIVE_PUSH_DEVICE"));
            return List.of(NotificationMapper.toResponse(message));
        }

        log.info("Creating push notification {} for recipient {} across {} active Firebase device(s)", request.templateId(), request.recipientRef(), devices.size());
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
        if (channel == Channel.IN_APP) {
            inboxRealtimePublisher.created(saved);
        }
        publishDispatch(saved.getId());
        return saved;
    }

    private NotificationMessage createSkippedMessage(
            NotificationDtos.SubmitNotificationRequest request,
            String idempotencyKey,
            Channel channel,
            String recipientRef,
            String reason
    ) {
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
        message.markSkipped(reason);
        return notificationRepository.save(message);
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

    private long unreadCount(String tenantId, String userId) {
        return notificationRepository.countByTenantIdAndRecipientRefAndChannelAndReadAtIsNull(
                tenantId,
                userId,
                Channel.IN_APP
        );
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

    private List<String> parseRecipients(String recipients) {
        List<String> parsed = Arrays.stream(defaultString(recipients).split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        return parsed.isEmpty() ? List.of("support@electrahub.net") : parsed;
    }
}
