package com.electrahub.notification.service;

import com.electrahub.notification.domain.Channel;
import com.electrahub.notification.domain.NotificationProject;
import com.electrahub.notification.domain.NotificationTemplate;
import com.electrahub.notification.repository.NotificationProjectRepository;
import com.electrahub.notification.repository.NotificationTemplateRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@DependsOn("notificationLiquibase")
public class NotificationTemplateCatalog implements ResourceLoaderAware {
    private static final Logger log = LoggerFactory.getLogger(NotificationTemplateCatalog.class);

    private final NotificationProjectRepository projectRepository;
    private final NotificationTemplateRepository templateRepository;
    private volatile Snapshot snapshot = Snapshot.empty();
    private ResourceLoader resourceLoader;

    public NotificationTemplateCatalog(
            NotificationProjectRepository projectRepository,
            NotificationTemplateRepository templateRepository
    ) {
        this.projectRepository = projectRepository;
        this.templateRepository = templateRepository;
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public synchronized void reload() {
        Map<String, ProjectConfiguration> projects = new HashMap<>();
        for (NotificationProject project : projectRepository.findByEnabledTrue()) {
            ProjectConfiguration configuration = loadProject(project);
            ProjectConfiguration duplicate = projects.put(normalizeKey(project.getProjectKey()), configuration);
            if (duplicate != null) {
                throw new IllegalStateException("Duplicate notification project: " + project.getProjectKey());
            }
        }

        Map<TemplateKey, NotificationTemplate> templates = new HashMap<>();
        for (NotificationTemplate template : templateRepository.findByEnabledTrue()) {
            validateTemplate(template, projects);
            TemplateKey key = new TemplateKey(
                    normalizeKey(template.getProjectKey()),
                    normalizeKey(template.getTemplateKey()),
                    template.getChannel(),
                    normalizeLocale(template.getLocale())
            );
            templates.merge(key, template, (current, candidate) ->
                    candidate.getVersion() > current.getVersion() ? candidate : current);
        }

        snapshot = new Snapshot(Map.copyOf(projects), Map.copyOf(templates));
        log.info("Loaded {} notification project(s) and {} active template(s) into memory",
                projects.size(), templates.size());
    }

    public Optional<ProjectConfiguration> project(String projectKey) {
        return Optional.ofNullable(snapshot.projects().get(normalizeKey(projectKey)));
    }

    public Optional<NotificationTemplate> template(
            String projectKey,
            String templateKey,
            Channel channel,
            String requestedLocale
    ) {
        Snapshot current = snapshot;
        String normalizedProject = normalizeKey(projectKey);
        ProjectConfiguration project = current.projects().get(normalizedProject);
        if (project == null || channel == null) {
            return Optional.empty();
        }
        String locale = requestedLocale == null || requestedLocale.isBlank()
                ? normalizeLocale(project.project().getDefaultLocale())
                : normalizeLocale(requestedLocale);
        NotificationTemplate exact = current.templates().get(
                new TemplateKey(normalizedProject, normalizeKey(templateKey), channel, locale));
        if (exact != null) {
            return Optional.of(exact);
        }
        return Optional.ofNullable(current.templates().get(
                new TemplateKey(
                        normalizedProject,
                        normalizeKey(templateKey),
                        channel,
                        normalizeLocale(project.project().getDefaultLocale())
                )));
    }

    public int loadedProjectCount() {
        return snapshot.projects().size();
    }

    public int loadedTemplateCount() {
        return snapshot.templates().size();
    }

    private ProjectConfiguration loadProject(NotificationProject project) {
        required(project.getProjectKey(), "project key");
        required(project.getDisplayName(), "project display name");
        required(project.getLegalName(), "project legal name");
        required(project.getLogoResource(), "project logo resource");
        required(project.getPrimaryColor(), "project primary color");
        required(project.getFromEmail(), "project from email");
        required(project.getFromName(), "project from name");
        required(project.getReplyToEmail(), "project reply-to email");
        required(project.getSupportEmail(), "project support email");
        required(project.getWebsiteUrl(), "project website URL");
        required(project.getBusinessAddress(), "project business address");
        required(project.getDefaultLocale(), "project default locale");
        required(project.getTimeZone(), "project time zone");
        Locale locale = Locale.forLanguageTag(project.getDefaultLocale());
        if (locale.getLanguage().isBlank()) {
            throw new IllegalStateException("Invalid project locale: " + project.getDefaultLocale());
        }
        if (!project.getPrimaryColor().matches("#[0-9A-Fa-f]{6}")) {
            throw new IllegalStateException("Invalid project primary color: " + project.getPrimaryColor());
        }
        ZoneId.of(project.getTimeZone());
        Resource logo = resourceLoader.getResource(resourceLocation(project.getLogoResource()));
        if (!logo.exists()) {
            throw new IllegalStateException("Project logo resource does not exist: " + project.getLogoResource());
        }
        try {
            return new ProjectConfiguration(
                    project,
                    logo.getContentAsByteArray(),
                    logoContentType(project.getLogoResource())
            );
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not load project logo: " + project.getLogoResource(), ex);
        }
    }

    private void validateTemplate(
            NotificationTemplate template,
            Map<String, ProjectConfiguration> projects
    ) {
        if (!projects.containsKey(normalizeKey(template.getProjectKey()))) {
            throw new IllegalStateException("Template references an unavailable project: " + template.getProjectKey());
        }
        required(template.getTemplateKey(), "template key");
        required(template.getSubjectTemplate(), "template subject");
        required(template.getBodyTemplate(), "template body");
        required(template.getContentType(), "template content type");
        if (template.getChannel() == null) {
            throw new IllegalStateException("Template channel is required");
        }
        if (template.getVersion() < 1) {
            throw new IllegalStateException("Template version must be positive");
        }
        normalizeLocale(template.getLocale());
    }

    private String logoContentType(String path) {
        String normalized = path.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return normalized.endsWith(".jpg") || normalized.endsWith(".jpeg") ? "image/jpeg" : "image/png";
    }

    static String resourceLocation(String path) {
        return path.startsWith("classpath:") ? path : "classpath:" + path;
    }

    private static String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeLocale(String value) {
        String normalized = value == null ? "" : value.trim().replace('_', '-');
        Locale locale = Locale.forLanguageTag(normalized);
        if (locale.getLanguage().isBlank()) {
            throw new IllegalStateException("Invalid notification template locale: " + value);
        }
        return locale.toLanguageTag();
    }

    private static void required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(field + " is required");
        }
    }

    private record TemplateKey(String projectKey, String templateKey, Channel channel, String locale) {
    }

    public record ProjectConfiguration(NotificationProject project, byte[] logoBytes, String logoContentType) {
        public ProjectConfiguration {
            logoBytes = Arrays.copyOf(logoBytes, logoBytes.length);
        }

        @Override
        public byte[] logoBytes() {
            return Arrays.copyOf(logoBytes, logoBytes.length);
        }
    }

    private record Snapshot(
            Map<String, ProjectConfiguration> projects,
            Map<TemplateKey, NotificationTemplate> templates
    ) {
        static Snapshot empty() {
            return new Snapshot(Map.of(), Map.of());
        }
    }
}
