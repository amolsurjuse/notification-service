package com.electrahub.notification.service;

public class EmailNotVerifiedException extends RuntimeException {
    public EmailNotVerifiedException() {
        super("Verify your email before changing email notification settings.");
    }
}
