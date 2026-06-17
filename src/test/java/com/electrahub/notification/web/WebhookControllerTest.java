package com.electrahub.notification.web;

import com.electrahub.notification.service.WebhookAuditService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WebhookControllerTest {
    @Test
    void rejectsUnsignedWebhookAndAuditsIt() {
        WebhookAuditService auditService = mock(WebhookAuditService.class);
        WebhookController controller = new WebhookController(auditService);

        var response = controller.receive("sendgrid", Map.of("id", "evt-1"), "{}");

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        verify(auditService).record(eq("sendgrid"), eq("provider-webhook"), eq("evt-1"), eq(false), eq("{}"));
    }
}
