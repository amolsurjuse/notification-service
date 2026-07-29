package com.electrahub.notification.service;

import com.electrahub.notification.domain.Channel;
import com.electrahub.notification.domain.NotificationMessage;
import com.electrahub.notification.domain.NotificationTemplate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmailCompositionServiceTest {
    @Test
    void composesTheDatabaseTemplateWithCodeOwnedReceiptDataAndAssets() throws Exception {
        NotificationTemplateCatalog catalog = mock(NotificationTemplateCatalog.class);
        NotificationTemplateCatalog.ProjectConfiguration project =
                ChargingReceiptEmailComposerTest.configuration();
        NotificationTemplate template = new NotificationTemplate(
                "electrahub",
                "charging-receipt-ready",
                Channel.EMAIL,
                "en-US",
                3,
                "Your [[${projectDisplayName}]] receipt - [[${receiptNumber}]]",
                "<h1 th:text=\"${stationName}\">Station</h1>",
                "text/html; charset=UTF-8",
                true
        );
        when(catalog.project("electrahub")).thenReturn(Optional.of(project));
        when(catalog.template("electrahub", "charging-receipt-ready", Channel.EMAIL, null))
                .thenReturn(Optional.of(template));

        ChargingReceiptPdfGenerator pdfGenerator = mock(ChargingReceiptPdfGenerator.class);
        when(pdfGenerator.generate(any(), any())).thenReturn("%PDF-test".getBytes());
        ChargingReceiptEmailComposer composer = new ChargingReceiptEmailComposer(pdfGenerator);
        EmailCompositionService service = new EmailCompositionService(
                catalog,
                new NotificationTemplateRenderer(),
                List.of(composer)
        );
        NotificationMessage message = new NotificationMessage(
                "electrahub",
                "event-1",
                "event-1",
                "user@example.com",
                Channel.EMAIL,
                "charging-receipt-ready"
        );

        ComposedEmail result = service.compose(message, Map.of(
                "sessionId", "session-1",
                "stationName", "Downtown Hub",
                "totalCost", 2.40,
                "currency", "USD"
        )).orElseThrow();

        assertThat(result.projectKey()).isEqualTo("electrahub");
        assertThat(result.subject()).isEqualTo("Your ElectraHub receipt - EH-SESSION1");
        assertThat(result.renderedBody()).contains("Downtown Hub");
        assertThat(result.templateVersion()).isEqualTo(3);
        assertThat(result.inlineResources()).singleElement()
                .satisfies(resource -> assertThat(resource.contentId()).isEqualTo("project-logo"));
        assertThat(result.attachments()).singleElement()
                .satisfies(attachment -> assertThat(attachment.contentType()).isEqualTo("application/pdf"));
    }
}
