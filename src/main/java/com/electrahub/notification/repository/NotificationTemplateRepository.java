package com.electrahub.notification.repository;

import com.electrahub.notification.domain.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {
    List<NotificationTemplate> findByEnabledTrue();
}
