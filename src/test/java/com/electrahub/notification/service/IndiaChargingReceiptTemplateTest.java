package com.electrahub.notification.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IndiaChargingReceiptTemplateTest {
    @Test
    void rendersTheCodeOwnedGstInvoiceWithTaxableValueAndSplitGst() throws Exception {
        String template = new ClassPathResource(
                "db/templates/electrahub/in/charging-receipt-email.html")
                .getContentAsString(StandardCharsets.UTF_8);
        NotificationTemplateRenderer renderer = new NotificationTemplateRenderer();

        String rendered = renderer.renderBody(
                template,
                "text/html; charset=UTF-8",
                variables(),
                Locale.forLanguageTag("en-IN")
        );

        assertThat(rendered)
                .contains("GST tax invoice")
                .contains("27AAECE0000A1Z5")
                .contains("Taxable value")
                .contains("CGST")
                .contains("SGST")
                .contains("INR 118.00")
                .doesNotContain("xmlns:th=")
                .doesNotContain("th:text=");
    }

    private Map<String, Object> variables() {
        return Map.ofEntries(
                Map.entry("projectDisplayName", "ElectraHub"),
                Map.entry("projectPrimaryColor", "#2563EB"),
                Map.entry("supportEmail", "support@electrahub.net"),
                Map.entry("receiptNumber", "EH-INDIA001"),
                Map.entry("sessionId", "india-session-001"),
                Map.entry("supplierLegalName", "ElectraHub India Pvt Ltd"),
                Map.entry("supplierTaxRegistration", "27AAECE0000A1Z5"),
                Map.entry("documentTitle", "GST tax invoice"),
                Map.entry("hasComplianceNotice", false),
                Map.entry("complianceNotice", ""),
                Map.entry("hasSupplierAddress", true),
                Map.entry("supplierAddress", "Pune, Maharashtra, India"),
                Map.entry("hasCustomer", false),
                Map.entry("customerName", ""),
                Map.entry("customerAddress", ""),
                Map.entry("customerTaxRegistration", ""),
                Map.entry("hasInvoiceDetails", true),
                Map.entry("serviceDescription", "Electric vehicle charging service"),
                Map.entry("classificationCode", "998714"),
                Map.entry("placeOfSupply", "27 Maharashtra"),
                Map.entry("countryDetails", List.of()),
                Map.entry("declarations", List.of("Tax is charged under GST.")),
                Map.entry("stationName", "Pune Charging Hub"),
                Map.entry("connectorLabel", "Connector 1"),
                Map.entry("sessionDate", "29 Jul 2026, 5:00 pm"),
                Map.entry("invoiceDate", "29 Jul 2026, 5:15 pm"),
                Map.entry("sessionDuration", "15 minutes"),
                Map.entry("energyDelivered", "1.500 kWh"),
                Map.entry("energyRate", "INR 18.00 / kWh"),
                Map.entry("chargingCost", "INR 100.00"),
                Map.entry("idleFee", "INR 0.00"),
                Map.entry("subscriptionDiscount", "-INR 0.00"),
                Map.entry("taxableAmount", "INR 100.00"),
                Map.entry("taxes", "INR 18.00"),
                Map.entry("totalCost", "INR 118.00"),
                Map.entry("paymentMethod", "Visa ending in 1111"),
                Map.entry("status", "Completed"),
                Map.entry("subscriptionPlanName", "Subscription"),
                Map.entry("hasIdleFee", false),
                Map.entry("hasSubscriptionDiscount", false),
                Map.entry("hasSubscriptionPlan", false),
                Map.entry("hasTaxLines", true),
                Map.entry("taxLines", List.of(
                        new ChargingReceiptEmailModel.TaxDisplayLine("CGST", "9%", "INR 100.00", "INR 9.00"),
                        new ChargingReceiptEmailModel.TaxDisplayLine("SGST", "9%", "INR 100.00", "INR 9.00")
                ))
        );
    }
}
