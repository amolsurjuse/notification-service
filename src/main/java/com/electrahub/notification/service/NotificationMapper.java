package com.electrahub.notification.service;

import com.electrahub.notification.domain.NotificationMessage;
import com.electrahub.notification.domain.UserContact;

public final class NotificationMapper {
    private NotificationMapper() {
    }

    public static NotificationDtos.NotificationResponse toResponse(NotificationMessage message) {
        return new NotificationDtos.NotificationResponse(
                message.getId(),
                message.getTenantId(),
                message.getEventId(),
                message.getRecipientRef(),
                message.getChannel(),
                message.getTemplateId(),
                message.getSubject(),
                message.getStatus(),
                message.getCreatedAt(),
                message.getDispatchedAt(),
                message.getReadAt()
        );
    }

    public static NotificationDtos.ContactResponse toResponse(UserContact contact) {
        return new NotificationDtos.ContactResponse(
                contact.getId(),
                contact.getTenantId(),
                contact.getUserId(),
                contact.getContactType(),
                contact.getMaskedDestination(),
                contact.getProvider(),
                contact.getPlatform(),
                contact.getStatus().name()
        );
    }
}
