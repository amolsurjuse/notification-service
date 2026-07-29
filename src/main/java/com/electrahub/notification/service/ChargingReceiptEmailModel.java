package com.electrahub.notification.service;

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
        String totalCost,
        String paymentMethod,
        String status,
        String subscriptionPlanName,
        boolean hasIdleFee,
        boolean hasSubscriptionDiscount,
        boolean hasSubscriptionPlan
) {
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
                Map.entry("totalCost", totalCost),
                Map.entry("paymentMethod", paymentMethod),
                Map.entry("status", status),
                Map.entry("subscriptionPlanName", subscriptionPlanName),
                Map.entry("hasIdleFee", hasIdleFee),
                Map.entry("hasSubscriptionDiscount", hasSubscriptionDiscount),
                Map.entry("hasSubscriptionPlan", hasSubscriptionPlan)
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
                Taxes: %s
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
                taxes,
                totalCost,
                paymentMethod,
                status,
                supportEmail
        ).trim();
    }
}
