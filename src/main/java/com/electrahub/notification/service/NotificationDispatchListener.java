package com.electrahub.notification.service;

import com.electrahub.notification.domain.Channel;
import com.electrahub.notification.domain.NotificationMessage;
import com.electrahub.notification.repository.NotificationMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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
        NotificationMessage message = notificationRepository.findById(command.notificationId())
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + command.notificationId()));
        ChannelAdapter adapter = adapters.get(message.getChannel());
        if (adapter == null) {
            message.markFailed("No channel adapter registered for " + message.getChannel());
            notificationRepository.save(message);
            return;
        }

        DispatchResult result = adapter.dispatch(message);
        if (result.success()) {
            message.markDispatched(result.provider(), result.providerMessageId());
            log.info("Notification {} dispatched on channel {} provider {}", message.getId(), message.getChannel(), result.provider());
        } else {
            message.markFailed(result.error());
            log.warn("Notification {} failed on channel {}: {}", message.getId(), message.getChannel(), result.error());
        }
        notificationRepository.save(message);
    }
}
