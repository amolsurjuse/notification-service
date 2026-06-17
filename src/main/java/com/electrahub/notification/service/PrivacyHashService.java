package com.electrahub.notification.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class PrivacyHashService {
    private final String salt;

    public PrivacyHashService(@Value("${notification.contact.ip-hash-salt}") String salt) {
        this.salt = salt == null ? "" : salt;
    }

    public String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((salt + ":" + value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    public String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "masked";
        }
        String[] parts = email.split("@", 2);
        String local = parts[0];
        String visible = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return visible + "***@" + parts[1];
    }

    public String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "masked";
        }
        return "***" + phone.substring(phone.length() - 4);
    }

    public String maskToken(String token) {
        if (token == null || token.length() < 8) {
            return "masked";
        }
        return token.substring(0, 4) + "***" + token.substring(token.length() - 4);
    }
}
