package com.electrahub.notification.service;

import com.electrahub.notification.domain.WebhookEvent;
import com.electrahub.notification.repository.WebhookEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookAuditService {
    private final WebhookEventRepository repository;
    private final PrivacyHashService privacyHashService;

    public WebhookAuditService(WebhookEventRepository repository, PrivacyHashService privacyHashService) {
        this.repository = repository;
        this.privacyHashService = privacyHashService;
    }

    @Transactional
    public void record(String provider, String eventType, String externalEventId, boolean signatureValid, String payload) {
        repository.save(new WebhookEvent(provider, eventType, externalEventId, signatureValid, privacyHashService.sha256(payload == null ? "" : payload)));
    }
}
