package com.electrahub.notification.service;

import java.util.List;
import java.util.Map;

public record ChargingReceiptEmailModel(
        String receiptNumber,
        String sessionId,
        String stationName,
        String connectorLabel,
        String sessionDate,
        String invoiceDate,
        String sessionDuration,
        String energyDelivered,
        String energyRate,
        String chargingCost,
        String idleFee,
        String subscriptionDiscount,
        String taxes,
        String taxableAmount,
        String totalCost,
        String paymentMethod,
        String status,
        String subscriptionPlanName,
        String taxCountryCode,
        String supplierTaxRegistration,
        String supplierLegalName,
        List<TaxDisplayLine> taxLines,
        String documentTitle,
        String complianceStatus,
        String supplierAddress,
        String supplierBusinessRegistration,
        String customerName,
        String customerAddress,
        String customerTaxRegistration,
        String serviceDescription,
        String classificationCode,
        String placeOfSupply,
        List<String> declarations,
        List<DetailLine> countryDetails,
        String complianceNotice,
        boolean hasIdleFee,
        boolean hasSubscriptionDiscount,
        boolean hasSubscriptionPlan,
        boolean hasTaxRegistration,
        boolean hasTaxLines,
        boolean hasSupplierAddress,
        boolean hasCustomer,
        boolean hasInvoiceDetails,
        boolean hasComplianceNotice
) {
    public ChargingReceiptEmailModel {
        taxLines = taxLines == null ? List.of() : List.copyOf(taxLines);
        declarations = declarations == null ? List.of() : List.copyOf(declarations);
        countryDetails = countryDetails == null ? List.of() : List.copyOf(countryDetails);
    }

    public record TaxDisplayLine(String label, String rate, String taxableAmount, String amount) {
    }

    public record DetailLine(String label, String value) {
    }

    public Map<String, Object> variables() {
        return Map.ofEntries(
                Map.entry("receiptNumber", receiptNumber),
                Map.entry("sessionId", sessionId),
                Map.entry("stationName", stationName),
                Map.entry("connectorLabel", connectorLabel),
                Map.entry("sessionDate", sessionDate),
                Map.entry("invoiceDate", invoiceDate),
                Map.entry("sessionDuration", sessionDuration),
                Map.entry("energyDelivered", energyDelivered),
                Map.entry("energyRate", energyRate),
                Map.entry("chargingCost", chargingCost),
                Map.entry("idleFee", idleFee),
                Map.entry("subscriptionDiscount", subscriptionDiscount),
                Map.entry("taxes", taxes),
                Map.entry("taxableAmount", taxableAmount),
                Map.entry("totalCost", totalCost),
                Map.entry("paymentMethod", paymentMethod),
                Map.entry("status", status),
                Map.entry("subscriptionPlanName", subscriptionPlanName),
                Map.entry("taxCountryCode", taxCountryCode),
                Map.entry("supplierTaxRegistration", supplierTaxRegistration),
                Map.entry("supplierLegalName", supplierLegalName),
                Map.entry("taxLines", taxLines),
                Map.entry("documentTitle", documentTitle),
                Map.entry("complianceStatus", complianceStatus),
                Map.entry("supplierAddress", supplierAddress),
                Map.entry("supplierBusinessRegistration", supplierBusinessRegistration),
                Map.entry("customerName", customerName),
                Map.entry("customerAddress", customerAddress),
                Map.entry("customerTaxRegistration", customerTaxRegistration),
                Map.entry("serviceDescription", serviceDescription),
                Map.entry("classificationCode", classificationCode),
                Map.entry("placeOfSupply", placeOfSupply),
                Map.entry("declarations", declarations),
                Map.entry("countryDetails", countryDetails),
                Map.entry("complianceNotice", complianceNotice),
                Map.entry("hasIdleFee", hasIdleFee),
                Map.entry("hasSubscriptionDiscount", hasSubscriptionDiscount),
                Map.entry("hasSubscriptionPlan", hasSubscriptionPlan),
                Map.entry("hasTaxRegistration", hasTaxRegistration),
                Map.entry("hasTaxLines", hasTaxLines),
                Map.entry("hasSupplierAddress", hasSupplierAddress),
                Map.entry("hasCustomer", hasCustomer),
                Map.entry("hasInvoiceDetails", hasInvoiceDetails),
                Map.entry("hasComplianceNotice", hasComplianceNotice)
        );
    }

    public String plainText(String projectName, String supportEmail) {
        return """
                %s charging receipt
                Receipt: %s
                Station: %s
                Connector: %s
                Date: %s
                Invoice issued: %s
                Duration: %s
                Energy: %s
                Charging: %s
                Idle fee: %s
                Discount: %s
                %s
                Total: %s
                Payment method: %s
                Status: %s
                Document: %s (%s)
                Supplier: %s
                Supplier address: %s
                Customer: %s
                Service: %s
                %s

                A PDF copy is attached. Questions: %s
                """.formatted(
                projectName,
                receiptNumber,
                stationName,
                connectorLabel,
                sessionDate,
                invoiceDate,
                sessionDuration,
                energyDelivered,
                chargingCost,
                idleFee,
                subscriptionDiscount,
                plainTextTaxes(),
                totalCost,
                paymentMethod,
                status,
                documentTitle,
                complianceStatus,
                supplierLegalName,
                supplierAddress,
                customerName,
                serviceDescription,
                plainTextLegalDetails(),
                supportEmail
        ).trim();
    }

    private String plainTextLegalDetails() {
        StringBuilder result = new StringBuilder();
        if (!classificationCode.isBlank()) {
            result.append("Classification: ").append(classificationCode).append(System.lineSeparator());
        }
        if (!placeOfSupply.isBlank()) {
            result.append("Place of supply: ").append(placeOfSupply).append(System.lineSeparator());
        }
        for (DetailLine detail : countryDetails) {
            result.append(detail.label()).append(": ").append(detail.value()).append(System.lineSeparator());
        }
        for (String declaration : declarations) {
            result.append(declaration).append(System.lineSeparator());
        }
        if (hasComplianceNotice) {
            result.append(complianceNotice);
        }
        return result.toString().trim();
    }

    private String plainTextTaxes() {
        if (taxLines.isEmpty()) {
            return "Taxes: " + taxes;
        }
        StringBuilder result = new StringBuilder();
        for (TaxDisplayLine line : taxLines) {
            if (!result.isEmpty()) {
                result.append(System.lineSeparator());
            }
            result.append(line.label()).append(" (").append(line.rate()).append(") on ")
                    .append(line.taxableAmount()).append(": ").append(line.amount());
        }
        if (hasTaxRegistration) {
            result.append(System.lineSeparator())
                    .append("Supplier tax registration: ")
                    .append(supplierTaxRegistration);
        }
        return result.toString();
    }
}
