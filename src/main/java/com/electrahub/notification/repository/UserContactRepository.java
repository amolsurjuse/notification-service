package com.electrahub.notification.repository;

import com.electrahub.notification.domain.ContactType;
import com.electrahub.notification.domain.UserContact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserContactRepository extends JpaRepository<UserContact, UUID> {
    Optional<UserContact> findByTenantIdAndUserIdAndContactTypeAndDestinationHash(String tenantId, String userId, ContactType contactType, String destinationHash);
}
