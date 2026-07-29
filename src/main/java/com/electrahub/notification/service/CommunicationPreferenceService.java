package com.electrahub.notification.service;

import com.electrahub.notification.domain.CommunicationPreference;
import com.electrahub.notification.repository.CommunicationPreferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class CommunicationPreferenceService {
    static final String EMAIL = "EMAIL";
    static final String ALL = "ALL";
    static final String RECEIPT = "RECEIPT";

    private static final Logger log = LoggerFactory.getLogger(CommunicationPreferenceService.class);

    private final CommunicationPreferenceRepository repository;
    private final UserPrincipalClient userPrincipalClient;

    public CommunicationPreferenceService(
            CommunicationPreferenceRepository repository,
            UserPrincipalClient userPrincipalClient
    ) {
        this.repository = repository;
        this.userPrincipalClient = userPrincipalClient;
    }

    @Transactional(readOnly = true)
    public NotificationDtos.CommunicationPreferencesResponse get(String tenantId, String userId) {
        UserPrincipalClient.UserPrincipal principal = userPrincipalClient.get(userId);
        boolean verified = principal != null && principal.isEmailVerified();
        boolean emailEnabled = enabled(tenantId, userId, EMAIL, ALL);
        boolean receiptEnabled = enabled(tenantId, userId, EMAIL, RECEIPT);
        String blockedReason = verified ? null : "Verify your email to manage and receive email notifications.";

        return new NotificationDtos.CommunicationPreferencesResponse(List.of(
                new NotificationDtos.ChannelPreferenceResponse(
                        EMAIL,
                        "Email",
                        emailEnabled,
                        verified,
                        verified && emailEnabled,
                        blockedReason,
                        List.of(new NotificationDtos.TopicPreferenceResponse(
                                RECEIPT,
                                "Email receipts",
                                receiptEnabled,
                                verified && emailEnabled && receiptEnabled
                        ))
                )
        ));
    }

    @Transactional
    public NotificationDtos.CommunicationPreferencesResponse updateEmail(
            String tenantId,
            String userId,
            NotificationDtos.UpdateEmailPreferencesRequest request
    ) {
        UserPrincipalClient.UserPrincipal principal = userPrincipalClient.get(userId);
        if (principal == null || !principal.isEmailVerified()) {
            throw new EmailNotVerifiedException();
        }
        upsert(tenantId, userId, EMAIL, ALL, request.enabled());
        upsert(tenantId, userId, EMAIL, RECEIPT, request.receiptEnabled());
        return get(tenantId, userId);
    }

    @Transactional(readOnly = true)
    public Optional<String> resolveReceiptEmail(String tenantId, String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        try {
            UserPrincipalClient.UserPrincipal principal = userPrincipalClient.get(userId);
            if (principal == null
                    || !principal.isEmailVerified()
                    || principal.email() == null
                    || principal.email().isBlank()
                    || !enabled(tenantId, userId, EMAIL, ALL)
                    || !enabled(tenantId, userId, EMAIL, RECEIPT)) {
                return Optional.empty();
            }
            return Optional.of(principal.email().trim());
        } catch (RuntimeException ex) {
            log.warn("Skipping optional receipt email because its verified recipient could not be resolved userId={}", userId);
            return Optional.empty();
        }
    }

    private boolean enabled(String tenantId, String userId, String channel, String topic) {
        return repository.findByTenantIdAndUserIdAndChannelAndTopic(tenantId, userId, channel, topic)
                .map(CommunicationPreference::isEnabled)
                .orElse(true);
    }

    private void upsert(String tenantId, String userId, String channel, String topic, boolean enabled) {
        CommunicationPreference preference = repository
                .findByTenantIdAndUserIdAndChannelAndTopic(tenantId, userId, channel, topic)
                .orElseGet(() -> new CommunicationPreference(tenantId, userId, channel, topic, enabled));
        preference.setEnabled(enabled);
        repository.save(preference);
    }
}
