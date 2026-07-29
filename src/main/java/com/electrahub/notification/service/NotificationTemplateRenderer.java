package com.electrahub.notification.service;

import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.util.Locale;
import java.util.Map;

@Service
public class NotificationTemplateRenderer {
    private final TemplateEngine htmlEngine = engine(TemplateMode.HTML);
    private final TemplateEngine textEngine = engine(TemplateMode.TEXT);

    public String renderSubject(String template, Map<String, Object> variables, Locale locale) {
        return textEngine.process(template, context(variables, locale)).trim();
    }

    public String renderBody(
            String template,
            String contentType,
            Map<String, Object> variables,
            Locale locale
    ) {
        TemplateEngine engine = contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("text/html")
                ? htmlEngine
                : textEngine;
        return engine.process(template, context(variables, locale));
    }

    private static TemplateEngine engine(TemplateMode mode) {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(mode);
        resolver.setCacheable(true);
        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private static Context context(Map<String, Object> variables, Locale locale) {
        Context context = new Context(locale == null ? Locale.US : locale);
        context.setVariables(variables == null ? Map.of() : variables);
        return context;
    }
}
