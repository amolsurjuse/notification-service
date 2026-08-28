package com.electrahub.notification.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BannerCampaignService {
    private final JdbcTemplate jdbcTemplate;
    private final UserPrincipalClient userPrincipalClient;
    private final Clock clock;

    public BannerCampaignService(JdbcTemplate jdbcTemplate, UserPrincipalClient userPrincipalClient) {
        this(jdbcTemplate, userPrincipalClient, Clock.systemUTC());
    }

    BannerCampaignService(JdbcTemplate jdbcTemplate, UserPrincipalClient userPrincipalClient, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.userPrincipalClient = userPrincipalClient;
        this.clock = clock;
    }

    public List<BannerResponse> eligible(String userId, String screen, String platform, String locale) {
        UserPrincipalClient.AudienceProfile audience = userPrincipalClient.audienceProfile(userId);
        OffsetDateTime now = OffsetDateTime.now(clock);
        return jdbcTemplate.query("""
                SELECT campaign_key,title,message,style,action_label,action_url,screen_names,platforms,locales,
                       included_countries,excluded_countries,registered_from,registered_until,starts_at,ends_at,
                       dismissible,priority,metadata::text
                  FROM notification.banner_campaign
                 WHERE active = true AND starts_at <= ? AND (ends_at IS NULL OR ends_at > ?)
                 ORDER BY priority DESC, updated_at DESC
                """, (rs, rowNum) -> map(rs), now, now).stream()
                .filter(c -> matches(c.screens(), screen))
                .filter(c -> matches(c.platforms(), platform))
                .filter(c -> matchesLocale(c.locales(), locale))
                .filter(c -> matchesCountry(c, audience.countryCode()))
                .filter(c -> matchesRegistration(c, audience.registeredAt()))
                .map(Campaign::response)
                .toList();
    }

    private static Campaign map(ResultSet rs) throws SQLException {
        return new Campaign(rs.getString("campaign_key"), rs.getString("title"), rs.getString("message"),
                rs.getString("style"), rs.getString("action_label"), rs.getString("action_url"),
                rs.getString("screen_names"), rs.getString("platforms"), rs.getString("locales"),
                rs.getString("included_countries"), rs.getString("excluded_countries"),
                rs.getObject("registered_from", OffsetDateTime.class), rs.getObject("registered_until", OffsetDateTime.class),
                rs.getObject("starts_at", OffsetDateTime.class), rs.getObject("ends_at", OffsetDateTime.class),
                rs.getBoolean("dismissible"), rs.getInt("priority"), rs.getString("metadata"));
    }

    static boolean matches(String configured, String actual) {
        Set<String> values = csv(configured);
        return values.contains("*") || values.contains(normalize(actual));
    }

    private static boolean matchesLocale(String configured, String actual) {
        Set<String> values = csv(configured);
        String normalized = normalize(actual);
        String language = normalized.contains("-") ? normalized.substring(0, normalized.indexOf('-')) : normalized;
        return values.contains("*") || values.contains(normalized) || values.contains(language);
    }

    private static boolean matchesCountry(Campaign campaign, String country) {
        String normalized = normalize(country);
        return !csv(campaign.excludedCountries()).contains(normalized)
                && matches(campaign.includedCountries(), normalized);
    }

    private static boolean matchesRegistration(Campaign campaign, OffsetDateTime registeredAt) {
        if (campaign.registeredFrom() == null && campaign.registeredUntil() == null) return true;
        if (registeredAt == null) return false;
        return (campaign.registeredFrom() == null || !registeredAt.isBefore(campaign.registeredFrom()))
                && (campaign.registeredUntil() == null || registeredAt.isBefore(campaign.registeredUntil()));
    }

    private static Set<String> csv(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return Arrays.stream(value.split(",")).map(BannerCampaignService::normalize)
                .filter(v -> !v.isBlank()).collect(Collectors.toUnmodifiableSet());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private record Campaign(String key, String title, String message, String style, String actionLabel,
                            String actionUrl, String screens, String platforms, String locales,
                            String includedCountries, String excludedCountries, OffsetDateTime registeredFrom,
                            OffsetDateTime registeredUntil, OffsetDateTime startsAt, OffsetDateTime endsAt,
                            boolean dismissible, int priority, String metadata) {
        BannerResponse response() {
            return new BannerResponse(key, title, message, style, actionLabel, actionUrl, dismissible,
                    priority, startsAt, endsAt, metadata);
        }
    }

    public record BannerResponse(String campaignKey, String title, String message, String style,
                                 String actionLabel, String actionUrl, boolean dismissible, int priority,
                                 OffsetDateTime startsAt, OffsetDateTime endsAt, String metadata) {
    }
}
