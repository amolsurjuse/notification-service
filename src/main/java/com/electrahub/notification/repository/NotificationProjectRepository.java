package com.electrahub.notification.repository;

import com.electrahub.notification.domain.NotificationProject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationProjectRepository extends JpaRepository<NotificationProject, String> {
    List<NotificationProject> findByEnabledTrue();
}
