package com.electrahub.notification.service;

import com.electrahub.notification.domain.Channel;
import com.electrahub.notification.repository.NotificationMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Service
public class InboxRealtimeService {
    private static final Logger log = LoggerFactory.getLogger(InboxRealtimeService.class);

    private final NotificationMessageRepository notificationRepository;
    private final Map<InboxOwner, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final long streamTimeoutMs;

    public InboxRealtimeService(
            NotificationMessageRepository notificationRepository,
            @Value("${notification.inbox.stream-timeout-ms:1800000}") long streamTimeoutMs
    ) {
        this.notificationRepository = notificationRepository;
        this.streamTimeoutMs = Math.max(60_000L, streamTimeoutMs);
    }

    public SseEmitter connect(String tenantId, String recipientRef) {
        InboxOwner owner = new InboxOwner(tenantId, recipientRef);
        SseEmitter emitter = new SseEmitter(streamTimeoutMs);
        emitters.computeIfAbsent(owner, ignored -> new CopyOnWriteArraySet<>()).add(emitter);
        emitter.onCompletion(() -> remove(owner, emitter));
        emitter.onTimeout(() -> remove(owner, emitter));
        emitter.onError(ignored -> remove(owner, emitter));

        long unreadCount = unreadCount(owner);
        send(owner, emitter, "ready", new NotificationDtos.InboxRealtimeResponse(
                "READY",
                null,
                unreadCount,
                OffsetDateTime.now()
        ));
        return emitter;
    }

    @RabbitListener(queues = "#{notificationRealtimeQueue.name}")
    public void onInboxChange(InboxRealtimeCommand command) {
        if (command == null || command.tenantId() == null || command.recipientRef() == null) {
            return;
        }
        InboxOwner owner = new InboxOwner(command.tenantId(), command.recipientRef());
        if (!emitters.containsKey(owner)) {
            return;
        }
        NotificationDtos.NotificationResponse notification = command.notificationId() == null
                ? null
                : notificationRepository.findByIdAndTenantIdAndRecipientRefAndChannel(
                        command.notificationId(),
                        command.tenantId(),
                        command.recipientRef(),
                        Channel.IN_APP
                ).map(NotificationMapper::toResponse).orElse(null);
        broadcast(owner, new NotificationDtos.InboxRealtimeResponse(
                command.type().name(),
                notification,
                unreadCount(owner),
                command.occurredAt() == null ? OffsetDateTime.now() : command.occurredAt()
        ));
    }

    @Scheduled(fixedDelayString = "${notification.inbox.heartbeat-ms:15000}")
    public void heartbeat() {
        emitters.forEach((owner, ownerEmitters) -> ownerEmitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().comment("keepalive"));
            } catch (IOException | IllegalStateException ex) {
                remove(owner, emitter);
            }
        }));
    }

    private void broadcast(InboxOwner owner, NotificationDtos.InboxRealtimeResponse response) {
        Set<SseEmitter> ownerEmitters = emitters.get(owner);
        if (ownerEmitters == null) {
            return;
        }
        ownerEmitters.forEach(emitter -> send(owner, emitter, "notification", response));
    }

    private void send(InboxOwner owner, SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException | IllegalStateException ex) {
            log.debug("Removing closed inbox stream for recipient {}", owner.recipientRef());
            remove(owner, emitter);
        }
    }

    private long unreadCount(InboxOwner owner) {
        return notificationRepository.countByTenantIdAndRecipientRefAndChannelAndReadAtIsNull(
                owner.tenantId(),
                owner.recipientRef(),
                Channel.IN_APP
        );
    }

    private void remove(InboxOwner owner, SseEmitter emitter) {
        Set<SseEmitter> ownerEmitters = emitters.get(owner);
        if (ownerEmitters == null) {
            return;
        }
        ownerEmitters.remove(emitter);
        if (ownerEmitters.isEmpty()) {
            emitters.remove(owner, ownerEmitters);
        }
    }

    private record InboxOwner(String tenantId, String recipientRef) {
    }
}
