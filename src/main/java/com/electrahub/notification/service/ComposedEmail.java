package com.electrahub.notification.service;

import java.util.List;

public record ComposedEmail(
        String projectKey,
        String subject,
        String plainTextBody,
        String renderedBody,
        String contentType,
        String fromEmail,
        String fromName,
        String replyToEmail,
        int templateVersion,
        List<EmailInlineResource> inlineResources,
        List<EmailAttachment> attachments
) {
    public ComposedEmail {
        inlineResources = inlineResources == null ? List.of() : List.copyOf(inlineResources);
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    public boolean html() {
        return contentType != null && contentType.toLowerCase().startsWith("text/html");
    }
}
