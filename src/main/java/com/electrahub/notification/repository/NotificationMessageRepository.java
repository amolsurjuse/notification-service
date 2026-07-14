package com.electrahub.notification.repository;

import com.electrahub.notification.domain.Channel;
import com.electrahub.notification.domain.DeliveryStatus;
import com.electrahub.notification.domain.NotificationMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationMessageRepository extends JpaRepository<NotificationMessage, UUID> {
    Optional<NotificationMessage> findByIdempotencyKeyAndChannelAndRecipientRef(String idempotencyKey, Channel channel, String recipientRef);
    List<NotificationMessage> findByRecipientRefOrderByCreatedAtDesc(String recipientRef);

    @Query("""
            select count(n)
            from NotificationMessage n
            where n.channel = :channel
              and n.attempts > 0
              and n.updatedAt >= :since
            """)
    long countAttemptedSince(@Param("channel") Channel channel, @Param("since") OffsetDateTime since);

    @Query("""
            select count(n)
            from NotificationMessage n
            where n.channel = :channel
              and n.status = :status
              and n.dispatchedAt >= :since
            """)
    long countDispatchedSince(
            @Param("channel") Channel channel,
            @Param("status") DeliveryStatus status,
            @Param("since") OffsetDateTime since
    );
}
