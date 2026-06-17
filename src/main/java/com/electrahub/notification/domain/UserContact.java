package com.electrahub.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_contacts", schema = "notification")
public class UserContact {
    @Id
    private UUID id;

    @Column(nullable = false, length = 128)
    private String tenantId;

    @Column(nullable = false, length = 160)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ContactType contactType;

    @Column(nullable = false, length = 128)
    private String destinationHash;

    @Column(nullable = false, length = 160)
    private String maskedDestination;

    @Column(length = 64)
    private String provider;

    @Column(length = 32)
    private String platform;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ContactStatus status;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    protected UserContact() {
    }

    public UserContact(String tenantId, String userId, ContactType contactType, String destinationHash, String maskedDestination) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.userId = userId;
        this.contactType = contactType;
        this.destinationHash = destinationHash;
        this.maskedDestination = maskedDestination;
        this.status = ContactStatus.ACTIVE;
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getUserId() { return userId; }
    public ContactType getContactType() { return contactType; }
    public String getDestinationHash() { return destinationHash; }
    public String getMaskedDestination() { return maskedDestination; }
    public String getProvider() { return provider; }
    public String getPlatform() { return platform; }
    public ContactStatus getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void setProvider(String provider) { this.provider = provider; }
    public void setPlatform(String platform) { this.platform = platform; }
    public void setStatus(ContactStatus status) { this.status = status; }
}
