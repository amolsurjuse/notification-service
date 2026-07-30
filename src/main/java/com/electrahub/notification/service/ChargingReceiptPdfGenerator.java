package com.electrahub.notification.service;

import com.electrahub.notification.domain.NotificationProject;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.text.Normalizer;

@Service
public class ChargingReceiptPdfGenerator {
    private static final String REGULAR_FONT = "notification-assets/fonts/DejaVuSans.ttf";
    private static final String BOLD_FONT = "notification-assets/fonts/DejaVuSans-Bold.ttf";

    public byte[] generate(
            ChargingReceiptEmailModel receipt,
            NotificationTemplateCatalog.ProjectConfiguration configuration
    ) {
        NotificationProject project = configuration.project();
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ReceiptFonts fonts = loadFonts(document);
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            try (PDPageContentStream canvas = new PDPageContentStream(document, page)) {
                boolean indiaReceipt = "IN".equalsIgnoreCase(receipt.taxCountryCode());
                int[] brand = color(project.getPrimaryColor());
                canvas.setNonStrokingColor(brand[0] / 255f, brand[1] / 255f, brand[2] / 255f);
                canvas.addRect(0, PDRectangle.LETTER.getHeight() - 10, PDRectangle.LETTER.getWidth(), 10);
                canvas.fill();

                PDImageXObject logo = PDImageXObject.createFromByteArray(
                        document,
                        configuration.logoBytes(),
                        "project-logo"
                );
                float logoWidth = 160;
                float logoHeight = logoWidth * logo.getHeight() / logo.getWidth();
                canvas.drawImage(logo, 48, 724, logoWidth, logoHeight);

                text(canvas, fonts.bold(), 22, 48, 684,
                        receipt.documentTitle(), 68, 82, 107);
                text(canvas, fonts.regular(), 10, 48, 666, receipt.receiptNumber(), 100, 116, 139);
                text(canvas, fonts.bold(), 12, 48, 632, receipt.stationName(), 15, 23, 42);
                text(canvas, fonts.regular(), 10, 48, 616, receipt.connectorLabel(), 100, 116, 139);
                text(canvas, fonts.bold(), 10, 380, 632, receipt.invoiceDate(), 15, 23, 42);
                text(canvas, fonts.regular(), 9, 380, 616, "Supply " + receipt.sessionDate(), 100, 116, 139);
                text(canvas, fonts.regular(), 9, 380, 602, receipt.sessionDuration(), 100, 116, 139);

                line(canvas, 48, 595, 564, 595, 203, 213, 225);
                float y = 570;
                y = row(canvas, fonts, "Energy delivered", receipt.energyDelivered(), y);
                y = row(canvas, fonts, "Energy rate", receipt.energyRate(), y);
                y = row(canvas, fonts, "Charging", receipt.chargingCost(), y);
                if (receipt.hasIdleFee()) {
                    y = row(canvas, fonts, "Idle fee", receipt.idleFee(), y);
                }
                if (receipt.hasSubscriptionDiscount()) {
                    y = row(canvas, fonts, "Subscription discount", receipt.subscriptionDiscount(), y);
                }
                if (indiaReceipt) {
                    y = row(canvas, fonts, "Taxable value", receipt.taxableAmount(), y);
                }
                if (receipt.hasTaxLines()) {
                    for (ChargingReceiptEmailModel.TaxDisplayLine taxLine : receipt.taxLines()) {
                        y = row(canvas, fonts, taxLine.label() + " taxable base", taxLine.taxableAmount(), y);
                        y = row(canvas, fonts, taxLine.label() + " (" + taxLine.rate() + ")", taxLine.amount(), y);
                    }
                } else {
                    y = row(canvas, fonts, "Taxes", receipt.taxes(), y);
                }
                line(canvas, 48, y + 8, 564, y + 8, 203, 213, 225);
                text(canvas, fonts.bold(), 14, 48, y - 16, "Total paid", 15, 23, 42);
                textRight(canvas, fonts.bold(), 18, 564, y - 16, receipt.totalCost(), brand[0], brand[1], brand[2]);

                float detailY = y - 62;
                text(canvas, fonts.regular(), 9, 48, detailY, "Payment method", 100, 116, 139);
                text(canvas, fonts.bold(), 11, 48, detailY - 17, receipt.paymentMethod(), 15, 23, 42);
                text(canvas, fonts.regular(), 9, 380, detailY, "Status", 100, 116, 139);
                text(canvas, fonts.bold(), 11, 380, detailY - 17, receipt.status(), 4, 120, 87);

                if (receipt.hasSubscriptionPlan()) {
                    text(canvas, fonts.regular(), 10, 48, detailY - 48,
                            "Plan: " + receipt.subscriptionPlanName(), 71, 85, 105);
                }
                if (receipt.hasTaxRegistration()) {
                    text(canvas, fonts.regular(), 9, 48, detailY - 65,
                            receipt.supplierLegalName()
                                    + (indiaReceipt ? " GSTIN: " : " tax registration: ")
                                    + receipt.supplierTaxRegistration(),
                            71, 85, 105);
                }
                if (receipt.hasComplianceNotice()) {
                    text(canvas, fonts.bold(), 8, 48, detailY - 82,
                            receipt.complianceNotice(), 180, 83, 9);
                }

                line(canvas, 48, 98, 564, 98, 226, 232, 240);
                text(canvas, fonts.regular(), 8, 48, 78,
                        indiaReceipt
                                ? receipt.supplierLegalName() + " | GSTIN " + receipt.supplierTaxRegistration()
                                : project.getLegalName() + " | " + project.getBusinessAddress(),
                        100, 116, 139);
                text(canvas, fonts.regular(), 8, 48, 64,
                        "Questions? " + project.getSupportEmail(), 100, 116, 139);
                textRight(canvas, fonts.regular(), 8, 564, 64,
                        "Session " + receipt.sessionId(), 100, 116, 139);
            }
            if (hasLegalDetails(receipt)) {
                addLegalDetailsPage(document, receipt, configuration, fonts);
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not generate charging receipt PDF", ex);
        }
    }

    private boolean hasLegalDetails(ChargingReceiptEmailModel receipt) {
        return receipt.hasTaxRegistration() || receipt.hasSupplierAddress() || receipt.hasCustomer()
                || receipt.hasInvoiceDetails() || receipt.hasComplianceNotice();
    }

    private void addLegalDetailsPage(
            PDDocument document,
            ChargingReceiptEmailModel receipt,
            NotificationTemplateCatalog.ProjectConfiguration configuration,
            ReceiptFonts fonts
    ) throws IOException {
        PDPage page = new PDPage(PDRectangle.LETTER);
        document.addPage(page);
        try (PDPageContentStream canvas = new PDPageContentStream(document, page)) {
            int[] brand = color(configuration.project().getPrimaryColor());
            canvas.setNonStrokingColor(brand[0] / 255f, brand[1] / 255f, brand[2] / 255f);
            canvas.addRect(0, PDRectangle.LETTER.getHeight() - 10, PDRectangle.LETTER.getWidth(), 10);
            canvas.fill();
            text(canvas, fonts.bold(), 18, 48, 742, receipt.documentTitle() + " details", 15, 23, 42);
            text(canvas, fonts.regular(), 9, 48, 724,
                    receipt.receiptNumber() + " | " + receipt.complianceStatus(), 100, 116, 139);

            float y = 690;
            y = legalSection(canvas, fonts, "Supplier", receipt.supplierLegalName(), y);
            y = legalLine(canvas, fonts, "Address", receipt.supplierAddress(), y);
            y = legalLine(canvas, fonts, "Tax registration", receipt.supplierTaxRegistration(), y);
            y = legalLine(canvas, fonts, "Business registration", receipt.supplierBusinessRegistration(), y);
            if (receipt.hasCustomer()) {
                y -= 8;
                y = legalSection(canvas, fonts, "Customer", receipt.customerName(), y);
                y = legalLine(canvas, fonts, "Address", receipt.customerAddress(), y);
                y = legalLine(canvas, fonts, "Tax registration", receipt.customerTaxRegistration(), y);
            }
            y -= 8;
            y = legalSection(canvas, fonts, "Supply", receipt.serviceDescription(), y);
            y = legalLine(canvas, fonts, "Classification", receipt.classificationCode(), y);
            y = legalLine(canvas, fonts, "Place of supply", receipt.placeOfSupply(), y);
            for (ChargingReceiptEmailModel.DetailLine detail : receipt.countryDetails()) {
                y = legalLine(canvas, fonts, detail.label(), detail.value(), y);
            }
            for (String declaration : receipt.declarations()) {
                y = legalLine(canvas, fonts, "Declaration", declaration, y);
            }
            if (receipt.hasComplianceNotice()) {
                y -= 8;
                legalLine(canvas, fonts, "Compliance notice", receipt.complianceNotice(), y);
            }

            line(canvas, 48, 72, 564, 72, 226, 232, 240);
            text(canvas, fonts.regular(), 8, 48, 52,
                    "Session " + receipt.sessionId(), 100, 116, 139);
        }
    }

    private float legalSection(
            PDPageContentStream canvas,
            ReceiptFonts fonts,
            String label,
            String value,
            float y
    ) throws IOException {
        text(canvas, fonts.bold(), 11, 48, y, label, 15, 23, 42);
        return writeWrapped(canvas, fonts.bold(), 10, 175, y, value, 15, 23, 42);
    }

    private float legalLine(
            PDPageContentStream canvas,
            ReceiptFonts fonts,
            String label,
            String value,
            float y
    ) throws IOException {
        if (value == null || value.isBlank()) {
            return y;
        }
        text(canvas, fonts.regular(), 9, 48, y, label, 100, 116, 139);
        return writeWrapped(canvas, fonts.regular(), 9, 175, y, value, 15, 23, 42);
    }

    private float writeWrapped(
            PDPageContentStream canvas,
            PDFont font,
            float size,
            float x,
            float y,
            String value,
            int red,
            int green,
            int blue
    ) throws IOException {
        String remaining = value == null ? "" : value.trim();
        float currentY = y;
        while (!remaining.isEmpty()) {
            int end = Math.min(74, remaining.length());
            if (end < remaining.length()) {
                int space = remaining.lastIndexOf(' ', end);
                if (space > 20) {
                    end = space;
                }
            }
            String line = remaining.substring(0, end).trim();
            text(canvas, font, size, x, currentY, line, red, green, blue);
            remaining = remaining.substring(end).trim();
            currentY -= 14;
        }
        return currentY - 8;
    }

    private float row(
            PDPageContentStream canvas,
            ReceiptFonts fonts,
            String label,
            String value,
            float y
    ) throws IOException {
        text(canvas, fonts.regular(), 10, 48, y, label, 71, 85, 105);
        textRight(canvas, fonts.bold(), 10, 564, y, value, 15, 23, 42);
        return y - 28;
    }

    private void text(
            PDPageContentStream canvas,
            PDFont font,
            float size,
            float x,
            float y,
            String value,
            int red,
            int green,
            int blue
    ) throws IOException {
        canvas.beginText();
        canvas.setFont(font, size);
        canvas.setNonStrokingColor(red / 255f, green / 255f, blue / 255f);
        canvas.newLineAtOffset(x, y);
        canvas.showText(pdfText(font, value, 74));
        canvas.endText();
    }

    private void textRight(
            PDPageContentStream canvas,
            PDFont font,
            float size,
            float right,
            float y,
            String value,
            int red,
            int green,
            int blue
    ) throws IOException {
        String safe = pdfText(font, value, 48);
        float width = font.getStringWidth(safe) / 1000 * size;
        text(canvas, font, size, right - width, y, safe, red, green, blue);
    }

    private void line(
            PDPageContentStream canvas,
            float x1,
            float y1,
            float x2,
            float y2,
            int red,
            int green,
            int blue
    ) throws IOException {
        canvas.setStrokingColor(red / 255f, green / 255f, blue / 255f);
        canvas.setLineWidth(0.7f);
        canvas.moveTo(x1, y1);
        canvas.lineTo(x2, y2);
        canvas.stroke();
    }

    private int[] color(String hex) {
        String normalized = hex == null ? "" : hex.trim().replace("#", "");
        if (!normalized.matches("[0-9A-Fa-f]{6}")) {
            normalized = "2563EB";
        }
        return new int[] {
                Integer.parseInt(normalized.substring(0, 2), 16),
                Integer.parseInt(normalized.substring(2, 4), 16),
                Integer.parseInt(normalized.substring(4, 6), 16)
        };
    }

    private ReceiptFonts loadFonts(PDDocument document) throws IOException {
        ClassPathResource regularResource = new ClassPathResource(REGULAR_FONT);
        ClassPathResource boldResource = new ClassPathResource(BOLD_FONT);
        try (InputStream regularInput = regularResource.getInputStream();
             InputStream boldInput = boldResource.getInputStream()) {
            return new ReceiptFonts(
                    PDType0Font.load(document, regularInput, true),
                    PDType0Font.load(document, boldInput, true)
            );
        }
    }

    private String pdfText(PDFont font, String value, int maxLength) throws IOException {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFC)
                .replace('\u00A0', ' ')
                .replace('\u202F', ' ')
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ');
        StringBuilder supported = new StringBuilder(normalized.length());
        for (int offset = 0; offset < normalized.length();) {
            int codePoint = normalized.codePointAt(offset);
            String glyph = new String(Character.toChars(codePoint));
            try {
                font.encode(glyph);
                supported.append(glyph);
            } catch (IllegalArgumentException unsupported) {
                supported.append('?');
            }
            offset += Character.charCount(codePoint);
        }
        int codePointCount = supported.codePointCount(0, supported.length());
        if (codePointCount <= maxLength) {
            return supported.toString();
        }
        int end = supported.offsetByCodePoints(0, maxLength - 3);
        return supported.substring(0, end) + "...";
    }

    private record ReceiptFonts(PDFont regular, PDFont bold) {
    }
}
