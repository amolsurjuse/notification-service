package com.electrahub.notification.service;

public record DispatchResult(boolean success, boolean skipped, boolean retryable, String provider, String providerMessageId, String error) {
    public static DispatchResult success(String provider, String providerMessageId) {
        return new DispatchResult(true, false, false, provider, providerMessageId, null);
    }

    public static DispatchResult failure(String provider, String error) {
        return new DispatchResult(false, false, false, provider, null, error);
    }

    public static DispatchResult retryableFailure(String provider, String error) {
        return new DispatchResult(false, false, true, provider, null, error);
    }

    public static DispatchResult skipped(String provider, String reason) {
        return new DispatchResult(false, true, false, provider, null, reason);
    }
}
