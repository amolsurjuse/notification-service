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
        when(catalog.template("electrahub", "charging-receipt-ready", Channel.EMAIL, null, null))
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

    @Test
    void selectsAndRendersTheIndiaGstTemplateFromTheTaxSnapshotCountry() throws Exception {
        NotificationTemplateCatalog catalog = mock(NotificationTemplateCatalog.class);
        NotificationTemplateCatalog.ProjectConfiguration project =
                ChargingReceiptEmailComposerTest.configuration();
        NotificationTemplate template = new NotificationTemplate(
                "electrahub",
                "charging-receipt-ready",
                Channel.EMAIL,
                "en-US",
                "IN",
                1,
                "GST receipt [[${receiptNumber}]]",
                "<h1>Tax invoice</h1><p th:text=\"${supplierTaxRegistration}\">GSTIN</p>"
                        + "<p th:each=\"line : ${taxLines}\" th:text=\"${line.label + ' ' + line.amount}\">Tax</p>",
                "text/html; charset=UTF-8",
                true
        );
        when(catalog.project("electrahub")).thenReturn(Optional.of(project));
        when(catalog.template("electrahub", "charging-receipt-ready", Channel.EMAIL, null, "IN"))
                .thenReturn(Optional.of(template));

        ChargingReceiptPdfGenerator pdfGenerator = mock(ChargingReceiptPdfGenerator.class);
        when(pdfGenerator.generate(any(), any())).thenReturn("%PDF-test".getBytes());
        EmailCompositionService service = new EmailCompositionService(
                catalog,
                new NotificationTemplateRenderer(),
                List.of(new ChargingReceiptEmailComposer(pdfGenerator))
        );
        NotificationMessage message = new NotificationMessage(
                "electrahub",
                "event-2",
                "event-2",
                "user@example.com",
                Channel.EMAIL,
                "charging-receipt-ready"
        );

        ComposedEmail result = service.compose(message, Map.ofEntries(
                Map.entry("sessionId", "india-session"),
                Map.entry("taxCountryCode", "IN"),
                Map.entry("supplierTaxRegistration", "27AAECE0000A1Z5"),
                Map.entry("currency", "INR"),
                Map.entry("totalCost", 118),
                Map.entry("taxesUsd", 18),
                Map.entry("taxBreakdown", List.of(
                        Map.of("displayName", "CGST", "rate", 9, "taxAmount", 9),
                        Map.of("displayName", "SGST", "rate", 9, "taxAmount", 9)
                ))
        )).orElseThrow();

        assertThat(result.subject()).isEqualTo("GST receipt EH-INDIASES");
        assertThat(result.renderedBody())
                .contains("Tax invoice")
                .contains("27AAECE0000A1Z5")
                .contains("CGST")
                .contains("SGST");
    }
}
