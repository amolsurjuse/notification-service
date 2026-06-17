package com.electrahub.notification.repository;

import com.electrahub.notification.domain.NotificationMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationMessageRepository extends JpaRepository<NotificationMessage, UUID> {
    Optional<NotificationMessage> findByIdempotencyKeyAndChannelAndRecipientRef(String idempotencyKey, com.electrahub.notification.domain.Channel channel, String recipientRef);
    List<NotificationMessage> findByRecipientRefOrderByCreatedAtDesc(String recipientRef);
}
