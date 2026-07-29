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
@Table(name = "notifications", schema = "notification")
public class NotificationMessage {
    @Id
    private UUID id;

    @Column(nullable = false, length = 128)
    private String tenantId;

    @Column(nullable = false, length = 128)
    private String eventId;

    @Column(nullable = false, length = 160)
    private String idempotencyKey;

    @Column(nullable = false, length = 160)
    private String recipientRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Channel channel;

    @Column(nullable = false, length = 128)
    private String templateId;

    @Column(length = 240)
    private String subject;

    @Column(columnDefinition = "text")
    private String body;

    @Column(length = 64)
    private String templateProjectKey;

    private Integer templateVersion;

    @Column(length = 80)
    private String renderedContentType;

    @Column(columnDefinition = "text")
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DeliveryStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(length = 64)
    private String provider;

    @Column(length = 160)
    private String providerMessageId;

    @Column(length = 1000)
    private String lastError;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    private OffsetDateTime dispatchedAt;
    private OffsetDateTime readAt;

    protected NotificationMessage() {
    }

    public NotificationMessage(String tenantId, String eventId, String idempotencyKey, String recipientRef, Channel channel, String templateId) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.eventId = eventId;
        this.idempotencyKey = idempotencyKey;
        this.recipientRef = recipientRef;
        this.channel = channel;
        this.templateId = templateId;
        this.status = DeliveryStatus.PENDING;
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
    public String getEventId() { return eventId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRecipientRef() { return recipientRef; }
    public Channel getChannel() { return channel; }
    public String getTemplateId() { return templateId; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public String getTemplateProjectKey() { return templateProjectKey; }
    public Integer getTemplateVersion() { return templateVersion; }
    public String getRenderedContentType() { return renderedContentType; }
    public String getPayloadJson() { return payloadJson; }
    public DeliveryStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public String getProvider() { return provider; }
    public String getProviderMessageId() { return providerMessageId; }
    public String getLastError() { return lastError; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public OffsetDateTime getDispatchedAt() { return dispatchedAt; }
    public OffsetDateTime getReadAt() { return readAt; }

    public void setSubject(String subject) { this.subject = subject; }
    public void setBody(String body) { this.body = body; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public void recordRenderedTemplate(
            String projectKey,
            int version,
            String contentType,
            String renderedSubject,
            String renderedBody
    ) {
        this.templateProjectKey = projectKey;
        this.templateVersion = version;
        this.renderedContentType = contentType;
        this.subject = renderedSubject;
        this.body = renderedBody;
    }

    public void markDispatched(String provider, String providerMessageId) {
        this.status = channel == Channel.IN_APP ? DeliveryStatus.DELIVERED : DeliveryStatus.DISPATCHED;
        this.provider = provider;
        this.providerMessageId = providerMessageId;
        this.dispatchedAt = OffsetDateTime.now();
        this.lastError = null;
        this.attempts++;
    }

    public void markFailed(String error) {
        this.status = DeliveryStatus.FAILED;
        this.lastError = error == null ? "unknown error" : error.substring(0, Math.min(error.length(), 1000));
        this.attempts++;
    }

    public void markSkipped(String reason) {
        this.status = DeliveryStatus.SKIPPED;
        this.lastError = reason == null ? "skipped" : reason.substring(0, Math.min(reason.length(), 1000));
        this.attempts++;
    }

    public void markRead() {
        this.status = DeliveryStatus.READ;
        if (this.readAt == null) {
            this.readAt = OffsetDateTime.now();
        }
    }

    public void markUnread() {
        if (channel != Channel.IN_APP) {
            throw new IllegalStateException("Only in-app notifications can be marked unread");
        }
        this.status = DeliveryStatus.DELIVERED;
        this.readAt = null;
    }
}
