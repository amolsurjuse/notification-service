package com.electrahub.notification.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class UserPrincipalClient {
    private final RestClient restClient;
    private final String internalToken;

    public UserPrincipalClient(
            @Value("${notification.user-service.base-url}") String baseUrl,
            @Value("${notification.user-service.internal-token:}") String internalToken
    ) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.internalToken = internalToken;
    }

    public UserPrincipal get(String userId) {
        return restClient.get()
                .uri("/api/v1/users/{userId}/principal", userId)
                .retrieve()
                .body(UserPrincipal.class);
    }

    public record UserPrincipal(String userId, String email, Boolean emailVerified) {
        public boolean isEmailVerified() {
            return Boolean.TRUE.equals(emailVerified);
        }
    }

    public AudienceProfile audienceProfile(String userId) {
        return restClient.get()
                .uri("/api/internal/users/{userId}/audience-profile", userId)
                .header("X-ElectraHub-Internal-Token", internalToken)
                .retrieve()
                .body(AudienceProfile.class);
    }

    public record AudienceProfile(String userId, String tenantId, String countryCode,
                                  java.time.OffsetDateTime registeredAt) {
    }
}
