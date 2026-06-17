package com.electrahub.notification.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

public interface FirebasePushSender {
    String send(Message message) throws Exception;
    boolean configured();

    static FirebasePushSender fromCredentials(
            String projectId,
            String serviceAccountPath,
            String serviceAccountJson,
            String serviceAccountBase64
    ) {
        InputStream credentialsStream = credentialsStream(serviceAccountPath, serviceAccountJson, serviceAccountBase64);
        if (credentialsStream == null) {
            return unconfigured();
        }
        try (credentialsStream) {
            FirebaseOptions.Builder options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentialsStream));
            if (hasText(projectId)) {
                options.setProjectId(projectId.trim());
            }
            FirebaseApp app = FirebaseApp.initializeApp(options.build(), "electrahub-notification-" + UUID.randomUUID());
            FirebaseMessaging messaging = FirebaseMessaging.getInstance(app);
            return new FirebaseAdminPushSender(messaging);
        } catch (IOException ex) {
            return unconfigured();
        }
    }

    static FirebasePushSender unconfigured() {
        return new FirebasePushSender() {
            @Override
            public String send(Message message) {
                throw new IllegalStateException("FIREBASE_NOT_CONFIGURED");
            }

            @Override
            public boolean configured() {
                return false;
            }
        };
    }

    private static InputStream credentialsStream(String path, String json, String base64) {
        try {
            if (hasText(json)) {
                return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
            }
            if (hasText(base64)) {
                return new ByteArrayInputStream(Base64.getDecoder().decode(base64.trim()));
            }
            if (hasText(path)) {
                return new FileInputStream(path.trim());
            }
            return null;
        } catch (IOException | IllegalArgumentException ex) {
            return null;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    final class FirebaseAdminPushSender implements FirebasePushSender {
        private final FirebaseMessaging messaging;

        private FirebaseAdminPushSender(FirebaseMessaging messaging) {
            this.messaging = Objects.requireNonNull(messaging, "messaging");
        }

        @Override
        public String send(Message message) throws Exception {
            return messaging.send(message);
        }

        @Override
        public boolean configured() {
            return true;
        }
    }
}
