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
@Table(name = "notification_templates", schema = "notification")
public class NotificationTemplate {
    @Id
    private UUID id;

    @Column(nullable = false, length = 64)
    private String projectKey;

    @Column(nullable = false, length = 128)
    private String templateKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Channel channel;

    @Column(nullable = false, length = 16)
    private String locale;

    @Column(nullable = false, length = 2)
    private String countryCode;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false, columnDefinition = "text")
    private String subjectTemplate;

    @Column(nullable = false, columnDefinition = "text")
    private String bodyTemplate;

    @Column(nullable = false, length = 80)
    private String contentType;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    protected NotificationTemplate() {
    }

    public NotificationTemplate(
            String projectKey,
            String templateKey,
            Channel channel,
            String locale,
            int version,
            String subjectTemplate,
            String bodyTemplate,
            String contentType,
            boolean enabled
    ) {
        this(projectKey, templateKey, channel, locale, "*", version, subjectTemplate, bodyTemplate, contentType, enabled);
    }

    public NotificationTemplate(
            String projectKey,
            String templateKey,
            Channel channel,
            String locale,
            String countryCode,
            int version,
            String subjectTemplate,
            String bodyTemplate,
            String contentType,
            boolean enabled
    ) {
        this.id = UUID.randomUUID();
        this.projectKey = projectKey;
        this.templateKey = templateKey;
        this.channel = channel;
        this.locale = locale;
        this.countryCode = countryCode;
        this.version = version;
        this.subjectTemplate = subjectTemplate;
        this.bodyTemplate = bodyTemplate;
        this.contentType = contentType;
        this.enabled = enabled;
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
    public String getProjectKey() { return projectKey; }
    public String getTemplateKey() { return templateKey; }
    public Channel getChannel() { return channel; }
    public String getLocale() { return locale; }
    public String getCountryCode() { return countryCode; }
    public int getVersion() { return version; }
    public String getSubjectTemplate() { return subjectTemplate; }
    public String getBodyTemplate() { return bodyTemplate; }
    public String getContentType() { return contentType; }
    public boolean isEnabled() { return enabled; }
}
