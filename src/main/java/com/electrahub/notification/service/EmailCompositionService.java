package com.electrahub.notification.service;

import com.electrahub.notification.domain.Channel;
import com.electrahub.notification.domain.NotificationMessage;
import com.electrahub.notification.domain.NotificationProject;
import com.electrahub.notification.domain.NotificationTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class EmailCompositionService {
    static final long MAX_TOTAL_ATTACHMENT_BYTES = 5L * 1024L * 1024L;
    private static final String PROJECT_LOGO_CONTENT_ID = "project-logo";

    private final NotificationTemplateCatalog catalog;
    private final NotificationTemplateRenderer renderer;
    private final Map<String, EmailComposer> composers;

    public EmailCompositionService(
            NotificationTemplateCatalog catalog,
            NotificationTemplateRenderer renderer,
            List<EmailComposer> composers
    ) {
        this.catalog = catalog;
        this.renderer = renderer;
        Map<String, EmailComposer> indexed = new HashMap<>();
        for (EmailComposer composer : composers) {
            String key = normalize(composer.templateKey());
            EmailComposer duplicate = indexed.put(key, composer);
            if (duplicate != null) {
                throw new IllegalStateException("Duplicate email composer for template: " + key);
            }
        }
        this.composers = Map.copyOf(indexed);
    }

    public Optional<ComposedEmail> compose(NotificationMessage message, Map<String, Object> payload) {
        String projectKey = normalize(message.getTenantId());
        NotificationTemplateCatalog.ProjectConfiguration configuration = catalog.project(projectKey)
                .orElse(null);
        if (configuration == null) {
            return Optional.empty();
        }

        NotificationProject project = configuration.project();
        String requestedLocale = text(payload == null ? null : payload.get("locale"));
        NotificationTemplate template = catalog.template(
                projectKey,
                message.getTemplateId(),
                Channel.EMAIL,
                requestedLocale
        ).orElse(null);
        if (template == null) {
            return Optional.empty();
        }

        EmailComposer composer = composers.get(normalize(template.getTemplateKey()));
        if (composer == null) {
            throw new IllegalStateException(
                    "No code-owned email composer is registered for template " + template.getTemplateKey());
        }

        EmailTemplateData data = composer.compose(payload == null ? Map.of() : payload, configuration);
        Map<String, Object> variables = new LinkedHashMap<>(data.variables());
        variables.put("projectDisplayName", project.getDisplayName());
        variables.put("projectLegalName", project.getLegalName());
        variables.put("projectPrimaryColor", project.getPrimaryColor());
        variables.put("projectWebsiteUrl", project.getWebsiteUrl());
        variables.put("supportEmail", project.getSupportEmail());
        variables.put("businessAddress", project.getBusinessAddress());

        Locale locale = locale(requestedLocale, project.getDefaultLocale());
        String subject = renderer.renderSubject(template.getSubjectTemplate(), variables, locale);
        String renderedBody = renderer.renderBody(
                template.getBodyTemplate(),
                template.getContentType(),
                variables,
                locale
        );
        validateAttachmentBudget(data.attachments());

        EmailInlineResource logo = new EmailInlineResource(
                PROJECT_LOGO_CONTENT_ID,
                configuration.logoContentType(),
                configuration.logoBytes()
        );
        return Optional.of(new ComposedEmail(
                project.getProjectKey(),
                subject,
                data.plainTextBody(),
                renderedBody,
                template.getContentType(),
                project.getFromEmail(),
                project.getFromName(),
                project.getReplyToEmail(),
                template.getVersion(),
                List.of(logo),
                data.attachments()
        ));
    }

    private void validateAttachmentBudget(List<EmailAttachment> attachments) {
        long total = attachments.stream().mapToLong(attachment -> attachment.content().length).sum();
        if (total > MAX_TOTAL_ATTACHMENT_BYTES) {
            throw new IllegalStateException("Email attachments exceed the 5 MB notification limit");
        }
    }

    private static Locale locale(String requested, String fallback) {
        String value = requested == null || requested.isBlank() ? fallback : requested;
        Locale locale = Locale.forLanguageTag(value.replace('_', '-'));
        return locale.getLanguage().isBlank() ? Locale.US : locale;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String result = String.valueOf(value).trim();
        return result.isEmpty() ? null : result;
    }
}
