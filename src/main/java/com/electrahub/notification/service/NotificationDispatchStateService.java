package com.electrahub.notification.service;

import com.electrahub.notification.domain.NotificationMessage;
import com.electrahub.notification.repository.NotificationMessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class NotificationDispatchStateService {
    private final NotificationMessageRepository notificationRepository;

    public NotificationDispatchStateService(NotificationMessageRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRetryableFailure(UUID notificationId, String error) {
        NotificationMessage message = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationId));
        message.markFailed(error);
        notificationRepository.save(message);
    }
}
