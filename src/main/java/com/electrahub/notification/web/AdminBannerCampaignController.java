package com.electrahub.notification.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/banner-campaigns")
public class AdminBannerCampaignController {
    private final JdbcTemplate jdbcTemplate;

    public AdminBannerCampaignController(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @GetMapping
    public List<BannerCampaignSummary> list() {
        return jdbcTemplate.query("""
                SELECT campaign_key,title,message,style,screen_names,platforms,locales,included_countries,
                       excluded_countries,registered_from,registered_until,starts_at,ends_at,active,
                       dismissible,priority,metadata::text
                  FROM notification.banner_campaign ORDER BY priority DESC, updated_at DESC
                """, (rs, row) -> new BannerCampaignSummary(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
                rs.getString(9), rs.getObject(10, OffsetDateTime.class), rs.getObject(11, OffsetDateTime.class),
                rs.getObject(12, OffsetDateTime.class), rs.getObject(13, OffsetDateTime.class), rs.getBoolean(14),
                rs.getBoolean(15), rs.getInt(16), rs.getString(17)));
    }

    @PutMapping("/{campaignKey}")
    public ResponseEntity<Void> upsert(
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_.-]{1,96}$") String campaignKey,
            @Valid @RequestBody UpsertBannerCampaign request
    ) {
        validateWindows(request);
        jdbcTemplate.update("""
                INSERT INTO notification.banner_campaign
                  (id,campaign_key,title,message,style,action_label,action_url,screen_names,platforms,locales,
                   included_countries,excluded_countries,registered_from,registered_until,starts_at,ends_at,
                   active,dismissible,priority,metadata)
                VALUES (gen_random_uuid(),?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CAST(? AS jsonb))
                ON CONFLICT (campaign_key) DO UPDATE SET
                  title=EXCLUDED.title,message=EXCLUDED.message,style=EXCLUDED.style,
                  action_label=EXCLUDED.action_label,action_url=EXCLUDED.action_url,
                  screen_names=EXCLUDED.screen_names,platforms=EXCLUDED.platforms,locales=EXCLUDED.locales,
                  included_countries=EXCLUDED.included_countries,excluded_countries=EXCLUDED.excluded_countries,
                  registered_from=EXCLUDED.registered_from,registered_until=EXCLUDED.registered_until,
                  starts_at=EXCLUDED.starts_at,ends_at=EXCLUDED.ends_at,active=EXCLUDED.active,
                  dismissible=EXCLUDED.dismissible,priority=EXCLUDED.priority,metadata=EXCLUDED.metadata,updated_at=now()
                """, campaignKey, request.title(), request.message(), request.style(), request.actionLabel(),
                request.actionUrl(), csv(request.screenNames()), csv(request.platforms()), csv(request.locales()),
                csv(request.includedCountries()), csvExclusions(request.excludedCountries()), request.registeredFrom(),
                request.registeredUntil(), request.startsAt(), request.endsAt(), request.active(),
                request.dismissible(), request.priority(), request.metadataJson());
        return ResponseEntity.noContent().build();
    }

    private static void validateWindows(UpsertBannerCampaign request) {
        if (request.endsAt() != null && !request.endsAt().isAfter(request.startsAt()))
            throw new IllegalArgumentException("endsAt must be after startsAt");
        if (request.registeredFrom() != null && request.registeredUntil() != null
                && !request.registeredUntil().isAfter(request.registeredFrom()))
            throw new IllegalArgumentException("registeredUntil must be after registeredFrom");
    }

    private static String csv(java.util.List<String> values) {
        return values == null || values.isEmpty() ? "*" : String.join(",", values).toUpperCase(java.util.Locale.ROOT);
    }

    private static String csvExclusions(java.util.List<String> values) {
        return values == null || values.isEmpty() ? "" : String.join(",", values).toUpperCase(java.util.Locale.ROOT);
    }

    public record UpsertBannerCampaign(
            @NotBlank @Size(max = 160) String title,
            @NotBlank @Size(max = 1000) String message,
            @NotBlank @Pattern(regexp = "^[A-Za-z_]{2,24}$") String style,
            @Size(max = 80) String actionLabel,
            @Size(max = 500) String actionUrl,
            @NotNull java.util.List<String> screenNames,
            @NotNull java.util.List<String> platforms,
            @NotNull java.util.List<String> locales,
            @NotNull java.util.List<String> includedCountries,
            @NotNull java.util.List<String> excludedCountries,
            OffsetDateTime registeredFrom,
            OffsetDateTime registeredUntil,
            @NotNull OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            boolean active,
            boolean dismissible,
            int priority,
            @NotBlank String metadataJson
    ) { }

    public record BannerCampaignSummary(String campaignKey, String title, String message, String style,
            String screenNames, String platforms, String locales, String includedCountries,
            String excludedCountries, OffsetDateTime registeredFrom, OffsetDateTime registeredUntil,
            OffsetDateTime startsAt, OffsetDateTime endsAt, boolean active, boolean dismissible,
            int priority, String metadata) { }
}
