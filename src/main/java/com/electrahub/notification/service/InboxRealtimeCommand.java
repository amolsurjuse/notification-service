package com.electrahub.notification.service;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InboxRealtimeCommand(
        Type type,
        String tenantId,
        String recipientRef,
        UUID notificationId,
        OffsetDateTime occurredAt
) {
    public enum Type {
        CREATED,
        UPDATED,
        READ_ALL
    }
}
