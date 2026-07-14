package com.electrahub.notification.service;

import java.util.UUID;

public class RetryableNotificationDispatchException extends RuntimeException {
    public RetryableNotificationDispatchException(UUID notificationId, String error) {
        super("Retryable notification dispatch failure notificationId=" + notificationId + " error=" + error);
    }
}
