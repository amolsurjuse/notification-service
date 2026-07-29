package com.electrahub.notification.service;

import java.util.List;
import java.util.Map;

public record ChargingReceiptEmailModel(
        String receiptNumber,
        String sessionId,
        String stationName,
        String connectorLabel,
        String sessionDate,
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
        boolean hasIdleFee,
        boolean hasSubscriptionDiscount,
        boolean hasSubscriptionPlan,
        boolean hasTaxRegistration,
        boolean hasTaxLines
) {
    public ChargingReceiptEmailModel {
        taxLines = taxLines == null ? List.of() : List.copyOf(taxLines);
    }

    public record TaxDisplayLine(String label, String rate, String amount) {
    }

    public Map<String, Object> variables() {
        return Map.ofEntries(
                Map.entry("receiptNumber", receiptNumber),
                Map.entry("sessionId", sessionId),
                Map.entry("stationName", stationName),
                Map.entry("connectorLabel", connectorLabel),
                Map.entry("sessionDate", sessionDate),
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
                Map.entry("hasIdleFee", hasIdleFee),
                Map.entry("hasSubscriptionDiscount", hasSubscriptionDiscount),
                Map.entry("hasSubscriptionPlan", hasSubscriptionPlan),
                Map.entry("hasTaxRegistration", hasTaxRegistration),
                Map.entry("hasTaxLines", hasTaxLines)
        );
    }

    public String plainText(String projectName, String supportEmail) {
        return """
                %s charging receipt
                Receipt: %s
                Station: %s
                Connector: %s
                Date: %s
                Duration: %s
                Energy: %s
                Charging: %s
                Idle fee: %s
                Discount: %s
                %s
                Total: %s
                Payment method: %s
                Status: %s

                A PDF copy is attached. Questions: %s
                """.formatted(
                projectName,
                receiptNumber,
                stationName,
                connectorLabel,
                sessionDate,
                sessionDuration,
                energyDelivered,
                chargingCost,
                idleFee,
                subscriptionDiscount,
                plainTextTaxes(),
                totalCost,
                paymentMethod,
                status,
                supportEmail
        ).trim();
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
            result.append(line.label()).append(" (").append(line.rate()).append("): ").append(line.amount());
        }
        if (hasTaxRegistration) {
            result.append(System.lineSeparator())
                    .append("Supplier tax registration: ")
                    .append(supplierTaxRegistration);
        }
        return result.toString();
    }
}
