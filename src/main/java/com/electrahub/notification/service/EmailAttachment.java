package com.electrahub.notification.service;

import java.util.Arrays;

public record EmailAttachment(String filename, String contentType, byte[] content) {
    public EmailAttachment {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Attachment filename is required");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("Attachment content type is required");
        }
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Attachment content is required");
        }
        content = Arrays.copyOf(content, content.length);
    }

    @Override
    public byte[] content() {
        return Arrays.copyOf(content, content.length);
    }
}
