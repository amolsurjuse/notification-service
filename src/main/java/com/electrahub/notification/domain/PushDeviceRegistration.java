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
@Table(name = "push_device_registrations", schema = "notification")
public class PushDeviceRegistration {
    @Id
    private UUID id;

    @Column(nullable = false, length = 128)
    private String tenantId;

    @Column(nullable = false, length = 160)
    private String userId;

    @Column(nullable = false, length = 160)
    private String deviceId;

    @Column(nullable = false, length = 32)
    private String platform;

    @Column(nullable = false, length = 64)
    private String provider;

    @Column(nullable = false, columnDefinition = "text")
    private String fcmToken;

    @Column(nullable = false, length = 128)
    private String tokenHash;

    @Column(nullable = false, length = 160)
    private String maskedToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ContactStatus status;

    @Column(nullable = false)
    private OffsetDateTime lastSeenAt;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    protected PushDeviceRegistration() {
    }

    public PushDeviceRegistration(String tenantId, String userId, String deviceId, String platform, String provider) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.userId = userId;
        this.deviceId = deviceId;
        this.platform = platform;
        this.provider = provider;
        this.status = ContactStatus.ACTIVE;
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (lastSeenAt == null) {
            lastSeenAt = now;
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
    public String getDeviceId() { return deviceId; }
    public String getPlatform() { return platform; }
    public String getProvider() { return provider; }
    public String getFcmToken() { return fcmToken; }
    public String getTokenHash() { return tokenHash; }
    public String getMaskedToken() { return maskedToken; }
    public ContactStatus getStatus() { return status; }
    public OffsetDateTime getLastSeenAt() { return lastSeenAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void updateToken(String fcmToken, String tokenHash, String maskedToken) {
        this.fcmToken = fcmToken;
        this.tokenHash = tokenHash;
        this.maskedToken = maskedToken;
        this.status = ContactStatus.ACTIVE;
        this.lastSeenAt = OffsetDateTime.now();
    }

    public void deactivate() {
        this.status = ContactStatus.DELETED;
        this.lastSeenAt = OffsetDateTime.now();
    }
}
