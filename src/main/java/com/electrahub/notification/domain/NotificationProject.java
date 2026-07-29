package com.electrahub.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "notification_projects", schema = "notification")
public class NotificationProject {
    @Id
    @Column(length = 64)
    private String projectKey;

    @Column(nullable = false, length = 160)
    private String displayName;

    @Column(nullable = false, length = 200)
    private String legalName;

    @Column(nullable = false, length = 300)
    private String logoResource;

    @Column(nullable = false, length = 16)
    private String primaryColor;

    @Column(nullable = false, length = 240)
    private String fromEmail;

    @Column(nullable = false, length = 160)
    private String fromName;

    @Column(nullable = false, length = 240)
    private String replyToEmail;

    @Column(nullable = false, length = 240)
    private String supportEmail;

    @Column(nullable = false, length = 300)
    private String websiteUrl;

    @Column(nullable = false, length = 500)
    private String businessAddress;

    @Column(nullable = false, length = 16)
    private String defaultLocale;

    @Column(nullable = false, length = 64)
    private String timeZone;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    protected NotificationProject() {
    }

    public NotificationProject(
            String projectKey,
            String displayName,
            String legalName,
            String logoResource,
            String primaryColor,
            String fromEmail,
            String fromName,
            String replyToEmail,
            String supportEmail,
            String websiteUrl,
            String businessAddress,
            String defaultLocale,
            String timeZone,
            boolean enabled
    ) {
        this.projectKey = projectKey;
        this.displayName = displayName;
        this.legalName = legalName;
        this.logoResource = logoResource;
        this.primaryColor = primaryColor;
        this.fromEmail = fromEmail;
        this.fromName = fromName;
        this.replyToEmail = replyToEmail;
        this.supportEmail = supportEmail;
        this.websiteUrl = websiteUrl;
        this.businessAddress = businessAddress;
        this.defaultLocale = defaultLocale;
        this.timeZone = timeZone;
        this.enabled = enabled;
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public String getProjectKey() { return projectKey; }
    public String getDisplayName() { return displayName; }
    public String getLegalName() { return legalName; }
    public String getLogoResource() { return logoResource; }
    public String getPrimaryColor() { return primaryColor; }
    public String getFromEmail() { return fromEmail; }
    public String getFromName() { return fromName; }
    public String getReplyToEmail() { return replyToEmail; }
    public String getSupportEmail() { return supportEmail; }
    public String getWebsiteUrl() { return websiteUrl; }
    public String getBusinessAddress() { return businessAddress; }
    public String getDefaultLocale() { return defaultLocale; }
    public String getTimeZone() { return timeZone; }
    public boolean isEnabled() { return enabled; }
}
