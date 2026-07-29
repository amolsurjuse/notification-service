package com.electrahub.notification.service;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTemplateRendererTest {
    private final NotificationTemplateRenderer renderer = new NotificationTemplateRenderer();

    @Test
    void rendersDynamicSubjectAndEscapesHtmlValues() {
        Map<String, Object> variables = Map.of(
                "projectDisplayName", "ElectraHub",
                "receiptNumber", "EH-1234",
                "stationName", "<script>alert('x')</script>"
        );

        String subject = renderer.renderSubject(
                "Your [[${projectDisplayName}]] receipt - [[${receiptNumber}]]",
                variables,
                Locale.US
        );
        String html = renderer.renderBody(
                "<p th:text=\"${stationName}\">Station</p>",
                "text/html",
                variables,
                Locale.US
        );

        assertThat(subject).isEqualTo("Your ElectraHub receipt - EH-1234");
        assertThat(html).contains("&lt;script&gt;").doesNotContain("<script>");
    }
}
