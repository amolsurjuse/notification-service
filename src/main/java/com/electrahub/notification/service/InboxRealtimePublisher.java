package com.electrahub.notification.service;

import com.electrahub.notification.domain.NotificationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;

@Service
public class InboxRealtimePublisher {
    private static final Logger log = LoggerFactory.getLogger(InboxRealtimePublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;

    public InboxRealtimePublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${notification.broker.realtime-exchange}") String exchange
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
    }

    public void created(NotificationMessage message) {
        publishAfterCommit(new InboxRealtimeCommand(
                InboxRealtimeCommand.Type.CREATED,
                message.getTenantId(),
                message.getRecipientRef(),
                message.getId(),
                message.getCreatedAt() == null ? OffsetDateTime.now() : message.getCreatedAt()
        ));
    }

    public void updated(NotificationMessage message) {
        publishAfterCommit(new InboxRealtimeCommand(
                InboxRealtimeCommand.Type.UPDATED,
                message.getTenantId(),
                message.getRecipientRef(),
                message.getId(),
                OffsetDateTime.now()
        ));
    }

    public void readAll(String tenantId, String recipientRef) {
        publishAfterCommit(new InboxRealtimeCommand(
                InboxRealtimeCommand.Type.READ_ALL,
                tenantId,
                recipientRef,
                null,
                OffsetDateTime.now()
        ));
    }

    private void publishAfterCommit(InboxRealtimeCommand command) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publish(command);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publish(command);
            }
        });
    }

    private void publish(InboxRealtimeCommand command) {
        try {
            rabbitTemplate.convertAndSend(exchange, "", command);
        } catch (RuntimeException ex) {
            log.warn(
                    "Inbox realtime event {} could not be published for recipient {}",
                    command.type(),
                    command.recipientRef(),
                    ex
            );
        }
    }
}
