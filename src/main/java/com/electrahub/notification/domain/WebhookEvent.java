package com.electrahub.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "webhook_events", schema = "notification")
public class WebhookEvent {
    @Id
    private UUID id;

    @Column(nullable = false, length = 64)
    private String provider;

    @Column(nullable = false, length = 128)
    private String eventType;

    @Column(length = 160)
    private String externalEventId;

    @Column(nullable = false)
    private boolean signatureValid;

    @Column(nullable = false, length = 128)
    private String payloadSha256;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    protected WebhookEvent() {
    }

    public WebhookEvent(String provider, String eventType, String externalEventId, boolean signatureValid, String payloadSha256) {
        this.id = UUID.randomUUID();
        this.provider = provider;
        this.eventType = eventType;
        this.externalEventId = externalEventId;
        this.signatureValid = signatureValid;
        this.payloadSha256 = payloadSha256;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public UUID getId() { return id; }
}
