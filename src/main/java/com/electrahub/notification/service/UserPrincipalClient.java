package com.electrahub.notification.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class UserPrincipalClient {
    private final RestClient restClient;

    public UserPrincipalClient(@Value("${notification.user-service.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
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
}
