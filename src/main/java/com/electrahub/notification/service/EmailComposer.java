package com.electrahub.notification.service;

import java.util.Map;

public interface EmailComposer {
    String templateKey();

    EmailTemplateData compose(Map<String, Object> payload, NotificationTemplateCatalog.ProjectConfiguration project);
}
