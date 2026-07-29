package com.electrahub.notification.service;

import com.electrahub.notification.domain.NotificationProject;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

@Service
public class ChargingReceiptPdfGenerator {
    private static final PDFont REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    public byte[] generate(
            ChargingReceiptEmailModel receipt,
            NotificationTemplateCatalog.ProjectConfiguration configuration
    ) {
        NotificationProject project = configuration.project();
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            try (PDPageContentStream canvas = new PDPageContentStream(document, page)) {
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

                text(canvas, BOLD, 22, 48, 684, "Charging receipt", 68, 82, 107);
                text(canvas, REGULAR, 10, 48, 666, receipt.receiptNumber(), 100, 116, 139);
                text(canvas, BOLD, 12, 48, 632, receipt.stationName(), 15, 23, 42);
                text(canvas, REGULAR, 10, 48, 616, receipt.connectorLabel(), 100, 116, 139);
                text(canvas, BOLD, 10, 380, 632, receipt.sessionDate(), 15, 23, 42);
                text(canvas, REGULAR, 10, 380, 616, receipt.sessionDuration(), 100, 116, 139);

                line(canvas, 48, 595, 564, 595, 203, 213, 225);
                float y = 570;
                y = row(canvas, "Energy delivered", receipt.energyDelivered(), y);
                y = row(canvas, "Energy rate", receipt.energyRate(), y);
                y = row(canvas, "Charging", receipt.chargingCost(), y);
                if (receipt.hasIdleFee()) {
                    y = row(canvas, "Idle fee", receipt.idleFee(), y);
                }
                if (receipt.hasSubscriptionDiscount()) {
                    y = row(canvas, "Subscription discount", receipt.subscriptionDiscount(), y);
                }
                y = row(canvas, "Taxes", receipt.taxes(), y);
                line(canvas, 48, y + 8, 564, y + 8, 203, 213, 225);
                text(canvas, BOLD, 14, 48, y - 16, "Total paid", 15, 23, 42);
                textRight(canvas, BOLD, 18, 564, y - 16, receipt.totalCost(), brand[0], brand[1], brand[2]);

                float detailY = y - 62;
                text(canvas, REGULAR, 9, 48, detailY, "Payment method", 100, 116, 139);
                text(canvas, BOLD, 11, 48, detailY - 17, receipt.paymentMethod(), 15, 23, 42);
                text(canvas, REGULAR, 9, 380, detailY, "Status", 100, 116, 139);
                text(canvas, BOLD, 11, 380, detailY - 17, receipt.status(), 4, 120, 87);

                if (receipt.hasSubscriptionPlan()) {
                    text(canvas, REGULAR, 10, 48, detailY - 48,
                            "Plan: " + receipt.subscriptionPlanName(), 71, 85, 105);
                }

                line(canvas, 48, 98, 564, 98, 226, 232, 240);
                text(canvas, REGULAR, 8, 48, 78,
                        project.getLegalName() + " | " + project.getBusinessAddress(), 100, 116, 139);
                text(canvas, REGULAR, 8, 48, 64,
                        "Questions? " + project.getSupportEmail(), 100, 116, 139);
                textRight(canvas, REGULAR, 8, 564, 64,
                        "Session " + receipt.sessionId(), 100, 116, 139);
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not generate charging receipt PDF", ex);
        }
    }

    private float row(PDPageContentStream canvas, String label, String value, float y) throws IOException {
        text(canvas, REGULAR, 10, 48, y, label, 71, 85, 105);
        textRight(canvas, BOLD, 10, 564, y, value, 15, 23, 42);
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
        canvas.showText(pdfText(value, 74));
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
        String safe = pdfText(value, 48);
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

    private String pdfText(String value, int maxLength) {
        String normalized = value == null ? "" : value.replaceAll("[^\\x20-\\x7E]", "?");
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength - 3) + "...";
    }
}
