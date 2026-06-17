package com.electrahub.notification.web;

import com.electrahub.notification.service.WebhookAuditService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/webhooks")
public class WebhookController {
    private final WebhookAuditService webhookAuditService;

    public WebhookController(WebhookAuditService webhookAuditService) {
        this.webhookAuditService = webhookAuditService;
    }

    @PostMapping("/{provider}")
    public ResponseEntity<Map<String, String>> receive(
            @PathVariable String provider,
            @RequestHeader Map<String, String> headers,
            @RequestBody(required = false) String body
    ) {
        boolean signaturePresent = headers.keySet().stream()
                .map(String::toLowerCase)
                .anyMatch(name -> name.contains("signature"));
        webhookAuditService.record(provider, "provider-webhook", headers.get("id"), signaturePresent, body);
        if (!signaturePresent) {
            return ResponseEntity.status(401).body(Map.of("status", "REJECTED", "message", "Webhook signature is required"));
        }
        return ResponseEntity.accepted().body(Map.of("status", "ACCEPTED"));
    }
}
