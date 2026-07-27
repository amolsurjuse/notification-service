package com.electrahub.notification.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
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
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public interface FirebasePushSender {
    String FIREBASE_MESSAGING_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

    String send(Message message) throws Exception;
    boolean configured();
    String projectId();
    void verifyCredentials() throws IOException;

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
            GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream);
            if (credentials.createScopedRequired()) {
                credentials = credentials.createScoped(List.of(FIREBASE_MESSAGING_SCOPE));
            }
            String credentialProjectId = credentials instanceof ServiceAccountCredentials serviceAccount
                    ? serviceAccount.getProjectId()
                    : null;
            String effectiveProjectId = firstText(projectId, credentialProjectId);
            if (!hasText(effectiveProjectId)) {
                throw new IllegalArgumentException("Firebase project ID is required");
            }
            if (hasText(projectId) && hasText(credentialProjectId)
                    && !projectId.trim().equals(credentialProjectId.trim())) {
                throw new IllegalArgumentException("Firebase project ID does not match the service account project");
            }
            FirebaseOptions.Builder options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .setProjectId(effectiveProjectId);
            FirebaseApp app = FirebaseApp.initializeApp(options.build(), "electrahub-notification-" + UUID.randomUUID());
            FirebaseMessaging messaging = FirebaseMessaging.getInstance(app);
            return new FirebaseAdminPushSender(messaging, credentials, effectiveProjectId);
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

            @Override
            public String projectId() {
                return "";
            }

            @Override
            public void verifyCredentials() throws IOException {
                throw new IOException("Firebase credentials are not configured");
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

    private static String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    final class FirebaseAdminPushSender implements FirebasePushSender {
        private final FirebaseMessaging messaging;
        private final GoogleCredentials credentials;
        private final String projectId;

        private FirebaseAdminPushSender(FirebaseMessaging messaging, GoogleCredentials credentials, String projectId) {
            this.messaging = Objects.requireNonNull(messaging, "messaging");
            this.credentials = Objects.requireNonNull(credentials, "credentials");
            this.projectId = Objects.requireNonNull(projectId, "projectId");
        }

        @Override
        public String send(Message message) throws Exception {
            return messaging.send(message);
        }

        @Override
        public boolean configured() {
            return true;
        }

        @Override
        public String projectId() {
            return projectId;
        }

        @Override
        public void verifyCredentials() throws IOException {
            if (credentials.refreshAccessToken() == null) {
                throw new IOException("Firebase credentials did not produce an access token");
            }
        }
    }
}
