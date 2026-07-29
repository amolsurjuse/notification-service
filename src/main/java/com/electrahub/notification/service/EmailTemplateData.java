package com.electrahub.notification.service;

import java.util.List;
import java.util.Map;

public record EmailTemplateData(
        Map<String, Object> variables,
        String plainTextBody,
        List<EmailAttachment> attachments
) {
    public EmailTemplateData {
        variables = variables == null ? Map.of() : Map.copyOf(variables);
        plainTextBody = plainTextBody == null ? "" : plainTextBody;
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }
}
