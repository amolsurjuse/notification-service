package com.electrahub.notification.service;

import com.electrahub.notification.domain.NotificationProject;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChargingReceiptEmailComposerTest {
    @Test
    void createsFormattedTemplateDataAndAReadablePdfReceipt() throws Exception {
        NotificationTemplateCatalog.ProjectConfiguration configuration = configuration();
        ChargingReceiptEmailComposer composer = new ChargingReceiptEmailComposer(
                new ChargingReceiptPdfGenerator());

        EmailTemplateData result = composer.compose(Map.ofEntries(
                Map.entry("sessionId", "038fea2e-30ba-4dbf-bf2a-fc74428bca9a"),
                Map.entry("stationName", "LOC-SFO-001"),
                Map.entry("connectorLabel", "CON-SFO-001"),
                Map.entry("totalCost", 4.52),
                Map.entry("currency", "USD"),
                Map.entry("energyKwh", 8.125),
                Map.entry("tariffPerKwh", 0.42),
                Map.entry("taxesUsd", 0.31),
                Map.entry("taxCountryCode", "IN"),
                Map.entry("supplierLegalName", "ElectraHub India Pvt Ltd"),
                Map.entry("supplierTaxRegistration", "29AAECE0000A1Z5"),
                Map.entry("invoice", Map.ofEntries(
                        Map.entry("invoiceNumber", "EHIN/2627/000001"),
                        Map.entry("documentType", "GST_TAX_INVOICE"),
                        Map.entry("complianceStatus", "COMPLIANT"),
                        Map.entry("supplier", Map.of(
                                "legalName", "ElectraHub India Pvt Ltd",
                                "address", "Bengaluru, Karnataka, India",
                                "taxRegistrationNumber", "29AAECE0000A1Z5"
                        )),
                        Map.entry("serviceDescription", "Electric vehicle charging service"),
                        Map.entry("classificationCode", "998714"),
                        Map.entry("placeOfSupply", "29 Karnataka"),
                        Map.entry("declarations", List.of("Tax is charged under GST.")),
                        Map.entry("countryAttributes", Map.of("Supplier state code", "29")),
                        Map.entry("complianceErrors", List.of())
                )),
                Map.entry("taxBreakdown", List.of(
                        Map.of(
                                "taxType", "CGST",
                                "displayName", "Central GST",
                                "rate", 9,
                                "taxableAmount", 1.72,
                                "taxAmount", 0.155
                        ),
                        Map.of(
                                "taxType", "SGST",
                                "displayName", "Karnataka GST",
                                "rate", 9,
                                "taxableAmount", 1.72,
                                "taxAmount", 0.155
                        )
                )),
                Map.entry("paymentMethod", "Visa ending in 4242"),
                Map.entry("status", "COMPLETED"),
                Map.entry("idleFee", 1.25),
                Map.entry("chargingCost", 3.50),
                Map.entry("subscriptionDiscountAmount", 0.54),
                Map.entry("subscriptionPlanName", "ElectraHub Plus"),
                Map.entry("startedAt", "2026-07-29T00:00:00Z"),
                Map.entry("stoppedAt", "2026-07-29T00:37:00Z")
        ), configuration);

        assertThat(result.variables())
                .containsEntry("receiptNumber", "EHIN/2627/000001")
                .containsEntry("energyDelivered", "8.125 kWh")
                .containsEntry("hasIdleFee", true)
                .containsEntry("hasSubscriptionDiscount", true)
                .containsEntry("hasTaxLines", true)
                .containsEntry("hasTaxRegistration", true)
                .containsEntry("taxableAmount", "$4.21");
        assertThat(result.plainTextBody())
                .contains("Central GST (9%) on $1.72: $0.16")
                .contains("Karnataka GST (9%) on $1.72: $0.16")
                .contains("Supplier tax registration: 29AAECE0000A1Z5");
        assertThat(result.attachments()).singleElement().satisfies(attachment -> {
            assertThat(attachment.filename()).isEqualTo("electrahub-receipt-EHIN-2627-000001.pdf");
            assertThat(attachment.contentType()).isEqualTo("application/pdf");
            assertThat(new String(attachment.content(), 0, 5)).isEqualTo("%PDF-");
            try (var document = Loader.loadPDF(attachment.content())) {
                String text = new PDFTextStripper().getText(document);
                assertThat(text)
                        .contains("GST tax invoice")
                        .contains("LOC-SFO-001")
                        .contains("Taxable value")
                        .contains("Central GST (9%)")
                        .contains("Karnataka GST (9%)")
                        .contains("GSTIN")
                        .contains("29AAECE0000A1Z5")
                        .contains("Total paid")
                        .contains("$4.52");
            }
        });
    }

    static NotificationTemplateCatalog.ProjectConfiguration configuration() throws Exception {
        NotificationProject project = new NotificationProject(
                "electrahub",
                "ElectraHub",
                "ElectraHub",
                "notification-assets/electrahub-logo.png",
                "#2563EB",
                "no-reply@electrahub.net",
                "ElectraHub",
                "support@electrahub.net",
                "support@electrahub.net",
                "https://electrahub.net",
                "741 Village Dr, Edison, NJ 08817, USA",
                "en-US",
                "America/New_York",
                true
        );
        byte[] logo = new ClassPathResource("notification-assets/electrahub-logo.png")
                .getContentAsByteArray();
        return new NotificationTemplateCatalog.ProjectConfiguration(project, logo, "image/png");
    }
}
