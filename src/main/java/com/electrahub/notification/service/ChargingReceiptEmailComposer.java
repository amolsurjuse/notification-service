package com.electrahub.notification.service;

import com.electrahub.notification.domain.NotificationProject;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ChargingReceiptEmailComposer implements EmailComposer {
    public static final String TEMPLATE_KEY = "charging-receipt-ready";

    private final ChargingReceiptPdfGenerator pdfGenerator;

    public ChargingReceiptEmailComposer(ChargingReceiptPdfGenerator pdfGenerator) {
        this.pdfGenerator = pdfGenerator;
    }

    @Override
    public String templateKey() {
        return TEMPLATE_KEY;
    }

    @Override
    public EmailTemplateData compose(
            Map<String, Object> payload,
            NotificationTemplateCatalog.ProjectConfiguration configuration
    ) {
        NotificationProject project = configuration.project();
        Locale locale = locale(project.getDefaultLocale());
        ZoneId zoneId = ZoneId.of(project.getTimeZone());
        String currency = defaultText(payload.get("currency"), "USD");

        BigDecimal total = decimal(payload, "totalCost");
        BigDecimal idleFee = decimal(payload, "idleFee");
        BigDecimal taxes = decimal(payload, "taxesUsd");
        BigDecimal taxableAmount = total.subtract(taxes).max(BigDecimal.ZERO);
        BigDecimal discount = decimal(payload, "subscriptionDiscountAmount");
        BigDecimal chargingCost = payload.containsKey("chargingCost")
                ? decimal(payload, "chargingCost")
                : total.subtract(idleFee).subtract(taxes).add(discount).max(BigDecimal.ZERO);

        String sessionId = defaultText(payload.get("sessionId"), "unknown");
        OffsetDateTime startedAt = timestamp(payload.get("startedAt"));
        OffsetDateTime stoppedAt = timestamp(payload.get("stoppedAt"));
        List<ChargingReceiptEmailModel.TaxDisplayLine> taxLines = taxLines(payload, currency, locale);
        Map<?, ?> invoice = map(payload.get("invoice"));
        OffsetDateTime issuedAt = timestamp(invoice.get("issuedAt"));
        Map<?, ?> supplier = map(invoice.get("supplier"));
        Map<?, ?> customer = map(invoice.get("customer"));
        String supplierTaxRegistration = defaultText(
                supplier.get("taxRegistrationNumber"), defaultText(payload.get("supplierTaxRegistration"), ""));
        String supplierLegalName = defaultText(
                supplier.get("legalName"), defaultText(payload.get("supplierLegalName"), project.getLegalName()));
        String documentType = defaultText(invoice.get("documentType"), "PAYMENT_RECEIPT");
        String complianceStatus = defaultText(invoice.get("complianceStatus"), "NON_FISCAL");
        List<String> declarations = strings(invoice.get("declarations"));
        List<String> complianceErrors = strings(invoice.get("complianceErrors"));
        List<ChargingReceiptEmailModel.DetailLine> countryDetails = details(invoice.get("countryAttributes"));
        String complianceNotice = complianceErrors.isEmpty()
                ? ""
                : "Not a tax invoice: " + String.join("; ", complianceErrors);
        ChargingReceiptEmailModel model = new ChargingReceiptEmailModel(
                defaultText(invoice.get("invoiceNumber"), receiptNumber(sessionId)),
                sessionId,
                defaultText(payload.get("stationName"), "Charging station"),
                defaultText(payload.get("connectorLabel"), "Connector"),
                formatDate(startedAt, zoneId, locale),
                formatDate(issuedAt == null ? stoppedAt : issuedAt, zoneId, locale),
                formatDuration(startedAt, stoppedAt),
                decimal(payload, "energyKwh").setScale(3, RoundingMode.HALF_UP).toPlainString() + " kWh",
                money(decimal(payload, "tariffPerKwh"), currency, locale) + " / kWh",
                money(chargingCost, currency, locale),
                money(idleFee, currency, locale),
                "-" + money(discount.abs(), currency, locale),
                money(taxes, currency, locale),
                money(taxableAmount, currency, locale),
                money(total, currency, locale),
                defaultText(payload.get("paymentMethod"), "ElectraHub Wallet"),
                titleCase(defaultText(payload.get("status"), "Completed")),
                defaultText(payload.get("subscriptionPlanName"), "Subscription"),
                defaultText(payload.get("taxCountryCode"), ""),
                supplierTaxRegistration,
                supplierLegalName,
                taxLines,
                documentTitle(documentType),
                titleCase(complianceStatus),
                defaultText(supplier.get("address"), ""),
                defaultText(supplier.get("businessRegistrationNumber"), ""),
                defaultText(customer.get("legalName"), ""),
                defaultText(customer.get("address"), ""),
                defaultText(customer.get("taxRegistrationNumber"), ""),
                defaultText(invoice.get("serviceDescription"), "Electric vehicle charging service"),
                defaultText(invoice.get("classificationCode"), ""),
                defaultText(invoice.get("placeOfSupply"), ""),
                declarations,
                countryDetails,
                complianceNotice,
                idleFee.signum() > 0,
                discount.signum() > 0,
                text(payload.get("subscriptionPlanName")) != null,
                !supplierTaxRegistration.isBlank(),
                !taxLines.isEmpty(),
                text(supplier.get("address")) != null,
                text(customer.get("legalName")) != null,
                !countryDetails.isEmpty() || !declarations.isEmpty()
                        || text(invoice.get("classificationCode")) != null
                        || text(invoice.get("placeOfSupply")) != null,
                !complianceNotice.isBlank()
        );

        byte[] pdf = pdfGenerator.generate(model, configuration);
        EmailAttachment attachment = new EmailAttachment(
                "electrahub-receipt-" + safeFilename(model.receiptNumber()) + ".pdf",
                "application/pdf",
                pdf
        );
        return new EmailTemplateData(
                model.variables(),
                model.plainText(project.getDisplayName(), project.getSupportEmail()),
                List.of(attachment)
        );
    }

    private Map<?, ?> map(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private List<String> strings(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream().map(this::text).filter(java.util.Objects::nonNull).toList();
    }

    private List<ChargingReceiptEmailModel.DetailLine> details(Object value) {
        if (!(value instanceof Map<?, ?> values)) {
            return List.of();
        }
        return values.entrySet().stream()
                .map(entry -> new ChargingReceiptEmailModel.DetailLine(
                        defaultText(entry.getKey(), "Detail"), defaultText(entry.getValue(), "")))
                .filter(detail -> !detail.value().isBlank())
                .toList();
    }

    private String documentTitle(String value) {
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "GST_TAX_INVOICE" -> "GST tax invoice";
            case "FULL_TAX_INVOICE" -> "Tax invoice";
            case "SIMPLIFIED_TAX_INVOICE" -> "Simplified tax invoice";
            case "SALES_RECEIPT" -> "Sales receipt";
            default -> "Payment receipt";
        };
    }

    private List<ChargingReceiptEmailModel.TaxDisplayLine> taxLines(
            Map<String, Object> payload,
            String currency,
            Locale locale
    ) {
        Object rawLines = payload.get("taxBreakdown");
        if (!(rawLines instanceof List<?> lines)) {
            return List.of();
        }
        return lines.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(line -> new ChargingReceiptEmailModel.TaxDisplayLine(
                        defaultText(line.get("displayName"), defaultText(line.get("taxType"), "Tax")),
                        decimal(line, "rate").stripTrailingZeros().toPlainString() + "%",
                        money(decimal(line, "taxableAmount"), currency, locale),
                        money(decimal(line, "taxAmount"), currency, locale)
                ))
                .toList();
    }

    private String money(BigDecimal amount, String currencyCode, Locale locale) {
        NumberFormat format = NumberFormat.getCurrencyInstance(locale);
        try {
            format.setCurrency(Currency.getInstance(currencyCode.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            format.setCurrency(Currency.getInstance("USD"));
        }
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(amount.setScale(2, RoundingMode.HALF_UP));
    }

    private Locale locale(String value) {
        Locale locale = Locale.forLanguageTag(value == null ? "" : value.replace('_', '-'));
        return locale.getLanguage().isBlank() ? Locale.US : locale;
    }

    private String formatDate(OffsetDateTime value, ZoneId zoneId, Locale locale) {
        if (value == null) {
            return "Date unavailable";
        }
        DateTimeFormatter formatter = DateTimeFormatter
                .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(locale)
                .withZone(zoneId);
        return formatter.format(value.toInstant());
    }

    private String formatDuration(OffsetDateTime startedAt, OffsetDateTime stoppedAt) {
        if (startedAt == null || stoppedAt == null || stoppedAt.isBefore(startedAt)) {
            return "Duration unavailable";
        }
        long minutes = Math.max(1, Duration.between(startedAt, stoppedAt).toMinutes());
        long hours = minutes / 60;
        long remainder = minutes % 60;
        if (hours == 0) {
            return minutes + (minutes == 1 ? " minute" : " minutes");
        }
        return hours + (hours == 1 ? " hour " : " hours ") + remainder + " minutes";
    }

    private OffsetDateTime timestamp(Object value) {
        String text = text(value);
        if (text == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private BigDecimal decimal(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        String text = text(value);
        if (text == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private String receiptNumber(String sessionId) {
        String normalized = sessionId.replace("-", "").toUpperCase(Locale.ROOT);
        String suffix = normalized.substring(0, Math.min(8, normalized.length()));
        return "EH-" + (suffix.isBlank() ? "RECEIPT" : suffix);
    }

    private String titleCase(String value) {
        String normalized = value.replace('_', ' ').toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return "Completed";
        }
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private String safeFilename(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "-");
    }

    private String defaultText(Object value, String fallback) {
        String result = text(value);
        return result == null ? fallback : result;
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String result = String.valueOf(value).trim();
        return result.isEmpty() ? null : result;
    }
}
