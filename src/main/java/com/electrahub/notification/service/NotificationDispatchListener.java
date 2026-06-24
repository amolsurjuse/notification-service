package com.electrahub.notification.service;

import com.electrahub.notification.domain.Channel;
import com.electrahub.notification.domain.NotificationMessage;
import com.electrahub.notification.repository.NotificationMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationDispatchListener {
    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchListener.class);

    private final NotificationMessageRepository notificationRepository;
    private final Map<Channel, ChannelAdapter> adapters;

    public NotificationDispatchListener(NotificationMessageRepository notificationRepository, List<ChannelAdapter> adapters) {
        this.notificationRepository = notificationRepository;
        this.adapters = new EnumMap<>(Channel.class);
        adapters.forEach(adapter -> this.adapters.put(adapter.channel(), adapter));
    }

    @RabbitListener(queues = "${notification.broker.dispatch-queue}")
    @Transactional
    public void dispatch(DispatchCommand command) {
        withDispatchTrace(command.notificationId(), () -> {
            NotificationMessage message = notificationRepository.findById(command.notificationId())
                    .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + command.notificationId()));
            ChannelAdapter adapter = adapters.get(message.getChannel());
            if (adapter == null) {
                message.markFailed("No channel adapter registered for " + message.getChannel());
                notificationRepository.save(message);
                return;
            }

            DispatchResult result;
            try {
                result = adapter.dispatch(message);
            } catch (RuntimeException ex) {
                result = DispatchResult.failure("notification-service", "dispatch ignored after adapter exception: " + ex.getMessage());
            }
            if (result.success()) {
                message.markDispatched(result.provider(), result.providerMessageId());
                log.info("Notification {} dispatched on channel {} provider {}", message.getId(), message.getChannel(), result.provider());
            } else if (result.skipped()) {
                message.markSkipped(result.error());
                log.info("Notification {} skipped on channel {}: {}", message.getId(), message.getChannel(), result.error());
            } else {
                message.markFailed(result.error());
                log.warn("Notification {} failed on channel {} and will not be retried: {}", message.getId(), message.getChannel(), result.error());
            }
            notificationRepository.save(message);
        });
    }

    private void withDispatchTrace(UUID notificationId, Runnable action) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        try {
            MDC.put("traceId", traceIdFrom(notificationId));
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

    private String traceIdFrom(UUID notificationId) {
        String seed = notificationId == null ? UUID.randomUUID().toString() : notificationId.toString();
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
    }
}
