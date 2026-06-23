package com.electrahub.notification.repository;

import com.electrahub.notification.domain.ContactStatus;
import com.electrahub.notification.domain.PushDeviceRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushDeviceRegistrationRepository extends JpaRepository<PushDeviceRegistration, UUID> {
    Optional<PushDeviceRegistration> findByTenantIdAndUserIdAndProviderAndDeviceId(
            String tenantId,
            String userId,
            String provider,
            String deviceId
    );

    Optional<PushDeviceRegistration> findByTenantIdAndProviderAndDeviceIdAndStatus(
            String tenantId,
            String provider,
            String deviceId,
            ContactStatus status
    );

    List<PushDeviceRegistration> findByTenantIdAndUserIdAndProviderAndStatus(
            String tenantId,
            String userId,
            String provider,
            ContactStatus status
    );
}
