package com.electrahub.notification.service;

import com.electrahub.notification.domain.Channel;
import com.electrahub.notification.domain.NotificationMessage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;

public class SmtpEmailAdapter implements ChannelAdapter {
    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {};
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final JavaMailSender mailSender;
    private final ObjectMapper objectMapper;
    private final String provider;
    private final String host;
    private final String fromEmail;
    private final String fromName;

    public SmtpEmailAdapter(
            JavaMailSender mailSender,
            ObjectMapper objectMapper,
            String provider,
            String host,
            String fromEmail,
            String fromName
    ) {
        this.mailSender = mailSender;
        this.objectMapper = objectMapper;
        this.provider = blankToDefault(provider, "smtp-email");
        this.host = trimToNull(host);
        this.fromEmail = trimToNull(fromEmail);
        this.fromName = trimToNull(fromName);
    }

    @Override
    public Channel channel() {
        return Channel.EMAIL;
    }

    @Override
    public DispatchResult dispatch(NotificationMessage message) {
        if (host == null) {
            return DispatchResult.failure(provider, "SMTP_NOT_CONFIGURED host is required");
        }
        if (!looksLikeEmail(fromEmail)) {
            return DispatchResult.failure(provider, "SMTP_FROM_EMAIL_REQUIRED");
        }

        Map<String, Object> payload = parsePayload(message.getPayloadJson());
        String to = resolveRecipient(message, payload);
        if (to == null) {
            return DispatchResult.skipped(provider, "EMAIL_RECIPIENT_REQUIRED");
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name()
            );
            helper.setFrom(fromAddress());
            helper.setTo(to);
            helper.setSubject(defaultString(message.getSubject(), "ElectraHub notification"));
            String htmlBody = firstText(payload, "htmlBody", "bodyHtml", "html");
            if (htmlBody != null) {
                helper.setText(defaultString(message.getBody(), ""), htmlBody);
            } else {
                helper.setText(defaultString(message.getBody(), ""));
            }
            mailSender.send(mimeMessage);
            return DispatchResult.success(provider, message.getId().toString());
        } catch (MailException | MessagingException | UnsupportedEncodingException ex) {
            return DispatchResult.failure(provider, ex.getClass().getSimpleName() + ": " + ex.getMessage());
        } catch (RuntimeException ex) {
            return DispatchResult.failure(provider, "SMTP_SEND_FAILED: " + ex.getMessage());
        }
    }

    private InternetAddress fromAddress() throws MessagingException, UnsupportedEncodingException {
        if (fromName == null) {
            return new InternetAddress(fromEmail);
        }
        return new InternetAddress(fromEmail, fromName, StandardCharsets.UTF_8.name());
    }

    private String resolveRecipient(NotificationMessage message, Map<String, Object> payload) {
        String fromPayload = firstText(payload, "email", "to", "recipientEmail", "emailAddress", "supportEmail");
        if (looksLikeEmail(fromPayload)) {
            return fromPayload;
        }
        String recipientRef = trimToNull(message.getRecipientRef());
        return looksLikeEmail(recipientRef) ? recipientRef : null;
    }

    private Map<String, Object> parsePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payloadJson, PAYLOAD_TYPE);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String firstText(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            String value = trimToNull(payload.get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private boolean looksLikeEmail(String value) {
        String trimmed = trimToNull(value);
        return trimmed != null && EMAIL_PATTERN.matcher(trimmed).matches();
    }

    private String trimToNull(Object value) {
        if (value == null) {
            return null;
        }
        String trimmed = String.valueOf(value).trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
