package com.electrahub.notification.service;

import java.util.Arrays;

public record EmailInlineResource(String contentId, String contentType, byte[] content) {
    public EmailInlineResource {
        if (contentId == null || contentId.isBlank()) {
            throw new IllegalArgumentException("Inline resource content ID is required");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("Inline resource content type is required");
        }
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Inline resource content is required");
        }
        content = Arrays.copyOf(content, content.length);
    }

    @Override
    public byte[] content() {
        return Arrays.copyOf(content, content.length);
    }
}
