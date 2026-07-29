package com.electrahub.notification.service;

import com.electrahub.notification.domain.Channel;
import com.electrahub.notification.domain.NotificationProject;
import com.electrahub.notification.domain.NotificationTemplate;
import com.electrahub.notification.repository.NotificationProjectRepository;
import com.electrahub.notification.repository.NotificationTemplateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationTemplateCatalogTest {
    @Test
    void loadsProjectsAndKeepsTheHighestEnabledTemplateVersionInMemory() {
        NotificationProjectRepository projects = mock(NotificationProjectRepository.class);
        NotificationTemplateRepository templates = mock(NotificationTemplateRepository.class);
        NotificationProject project = project();
        NotificationTemplate versionOne = template(1, "Version one");
        NotificationTemplate versionTwo = template(2, "Version two");
        when(projects.findByEnabledTrue()).thenReturn(List.of(project));
        when(templates.findByEnabledTrue()).thenReturn(List.of(versionOne, versionTwo));

        NotificationTemplateCatalog catalog = new NotificationTemplateCatalog(projects, templates);
        catalog.setResourceLoader(new DefaultResourceLoader());
        catalog.reload();

        assertThat(catalog.loadedProjectCount()).isEqualTo(1);
        assertThat(catalog.loadedTemplateCount()).isEqualTo(1);
        assertThat(catalog.project("ElectraHub").orElseThrow().logoBytes()).isNotEmpty();
        assertThat(catalog.template("electrahub", "charging-receipt-ready", Channel.EMAIL, "en-US"))
                .get()
                .satisfies(selected -> {
                    assertThat(selected.getVersion()).isEqualTo(2);
                    assertThat(selected.getSubjectTemplate()).isEqualTo("Version two");
                });
    }

    private NotificationProject project() {
        return new NotificationProject(
                "electrahub",
                "ElectraHub",
                "ElectraHub",
                "notification-assets/electrahub-logo.png",
                "#2563EB",
                "no-reply@electrahub.net",
                "ElectraHub",
                "support@electrahub.net",
                "support@electrahub.net",
                "https://electrahub.net",
                "741 Village Dr, Edison, NJ 08817, USA",
                "en-US",
                "America/New_York",
                true
        );
    }

    private NotificationTemplate template(int version, String subject) {
        return new NotificationTemplate(
                "ELECTRAHUB",
                "Charging-Receipt-Ready",
                Channel.EMAIL,
                "en-US",
                version,
                subject,
                "<p th:text=\"${stationName}\">Station</p>",
                "text/html; charset=UTF-8",
                true
        );
    }
}
