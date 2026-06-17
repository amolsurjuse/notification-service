package com.electrahub.notification.service;

public record DispatchResult(boolean success, String provider, String providerMessageId, String error) {
    public static DispatchResult success(String provider, String providerMessageId) {
        return new DispatchResult(true, provider, providerMessageId, null);
    }

    public static DispatchResult failure(String provider, String error) {
        return new DispatchResult(false, provider, null, error);
    }
}
