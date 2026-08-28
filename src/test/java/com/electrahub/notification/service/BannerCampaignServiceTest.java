package com.electrahub.notification.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BannerCampaignServiceTest {
    @Test
    void matchesExactOrWildcardPlacementIgnoringCase() {
        assertThat(BannerCampaignService.matches("SIGN_IN,DASHBOARD", "dashboard")).isTrue();
        assertThat(BannerCampaignService.matches("*", "payments")).isTrue();
        assertThat(BannerCampaignService.matches("SIGN_IN", "payments")).isFalse();
    }
}
