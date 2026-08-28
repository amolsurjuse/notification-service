package com.electrahub.notification.web;

import com.electrahub.notification.service.BannerCampaignService;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/me/banners")
public class BannerCampaignController {
    private final BannerCampaignService service;

    public BannerCampaignController(BannerCampaignService service) { this.service = service; }

    @GetMapping
    public List<BannerCampaignService.BannerResponse> eligible(
            @RequestHeader(NotificationController.AUTHENTICATED_USER_HEADER) String userId,
            @RequestParam @Pattern(regexp = "^[A-Za-z0-9_.-]{1,64}$") String screen,
            @RequestParam @Pattern(regexp = "^[A-Za-z0-9_.-]{1,32}$") String platform,
            @RequestParam(defaultValue = "en") @Pattern(regexp = "^[A-Za-z0-9_-]{2,16}$") String locale
    ) {
        return service.eligible(userId, screen, platform, locale);
    }
}
