package com.electrahub.notification.service;

import com.electrahub.notification.domain.Channel;
import com.electrahub.notification.domain.NotificationMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmtpEmailAdapterTest {
    @Test
    void sendsRenderedHtmlWithInlineLogoAndPdfAttachment() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        EmailCompositionService composition = mock(EmailCompositionService.class);
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(composition.compose(any(), any())).thenReturn(Optional.of(new ComposedEmail(
                "electrahub",
                "Your ElectraHub receipt",
                "Plain receipt",
                "<html><body><img src=\"cid:project-logo\">Receipt</body></html>",
                "text/html; charset=UTF-8",
                "no-reply@electrahub.net",
                "ElectraHub",
                "support@electrahub.net",
                1,
                List.of(new EmailInlineResource("project-logo", "image/png", new byte[] {1, 2, 3})),
                List.of(new EmailAttachment("receipt.pdf", "application/pdf", "%PDF-test".getBytes()))
        )));
        SmtpEmailAdapter adapter = new SmtpEmailAdapter(
                mailSender,
                new ObjectMapper(),
                composition,
                "smtp-email",
                "smtp-relay.brevo.com",
                "fallback@electrahub.net",
                "ElectraHub"
        );
        NotificationMessage message = new NotificationMessage(
                "electrahub",
                "event-1",
                "event-1",
                "user@example.com",
                Channel.EMAIL,
                "charging-receipt-ready"
        );
        message.setBody("Fallback");
        message.setPayloadJson("{}");

        DispatchResult result = adapter.dispatch(message);

        assertThat(result.success()).isTrue();
        assertThat(mimeMessage.getSubject()).isEqualTo("Your ElectraHub receipt");
        assertThat(mimeMessage.getFrom()[0].toString()).contains("ElectraHub");
        assertThat(mimeMessage.getReplyTo()[0].toString()).isEqualTo("support@electrahub.net");
        assertThat(message.getTemplateProjectKey()).isEqualTo("electrahub");
        assertThat(message.getTemplateVersion()).isEqualTo(1);
        assertThat(message.getRenderedContentType()).isEqualTo("text/html; charset=UTF-8");
        assertThat(message.getSubject()).isEqualTo("Your ElectraHub receipt");
        assertThat(message.getBody()).contains("cid:project-logo");
        mimeMessage.saveChanges();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        mimeMessage.writeTo(output);
        String raw = output.toString(StandardCharsets.ISO_8859_1);
        assertThat(raw)
                .contains("Content-ID: <project-logo>")
                .contains("filename=receipt.pdf")
                .contains("Content-Type: application/pdf");
        verify(mailSender).send(mimeMessage);
    }
}
