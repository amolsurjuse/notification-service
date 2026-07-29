package com.electrahub.notification.repository;

import com.electrahub.notification.domain.CommunicationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommunicationPreferenceRepository extends JpaRepository<CommunicationPreference, UUID> {
    List<CommunicationPreference> findByTenantIdAndUserIdAndChannel(String tenantId, String userId, String channel);

    Optional<CommunicationPreference> findByTenantIdAndUserIdAndChannelAndTopic(
            String tenantId,
            String userId,
            String channel,
            String topic
    );
}
