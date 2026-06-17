package com.electrahub.notification.service;

import com.electrahub.notification.domain.Channel;
import com.electrahub.notification.domain.NotificationMessage;
import com.electrahub.notification.repository.NotificationMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;

public class QuotaLimitedEmailAdapter implements ChannelAdapter {
    private static final Logger log = LoggerFactory.getLogger(QuotaLimitedEmailAdapter.class);
    private static final Duration QUOTA_WINDOW = Duration.ofHours(24);

    private final NotificationMessageRepository notificationRepository;
    private final ChannelAdapter delegate;
    private final Clock clock;
    private final boolean enabled;
    private final int dailyLimit;
    private final int ratePerSecond;
    private Instant nextAllowedSendAt;

    public QuotaLimitedEmailAdapter(
            NotificationMessageRepository notificationRepository,
            ChannelAdapter delegate,
            Clock clock,
            boolean enabled,
            int dailyLimit,
            int ratePerSecond
    ) {
        this.notificationRepository = notificationRepository;
        this.delegate = delegate;
        this.clock = clock;
        this.enabled = enabled;
        this.dailyLimit = Math.max(0, dailyLimit);
        this.ratePerSecond = Math.max(1, ratePerSecond);
        this.nextAllowedSendAt = Instant.EPOCH;
    }

    @Override
    public Channel channel() {
        return Channel.EMAIL;
    }

    @Override
    public DispatchResult dispatch(NotificationMessage message) {
        if (!enabled) {
            return DispatchResult.failure("ses-email", "EMAIL_DISABLED");
        }

        OffsetDateTime since = OffsetDateTime.now(clock).minus(QUOTA_WINDOW);
        long attemptsInWindow = notificationRepository.countAttemptedSince(Channel.EMAIL, since);
        if (attemptsInWindow >= dailyLimit) {
            return DispatchResult.failure("ses-email", "EMAIL_DAILY_QUOTA_EXCEEDED rollingWindowHours=24 limit=" + dailyLimit);
        }

        throttle();
        DispatchResult result = delegate.dispatch(message);
        if (!result.success()) {
            log.warn("Email provider failure ignored without retry for notification {}: {}", message.getId(), result.error());
        }
        return result;
    }

    private synchronized void throttle() {
        Instant now = clock.instant();
        if (now.isBefore(nextAllowedSendAt)) {
            sleepUntil(nextAllowedSendAt, now);
        }
        long spacingMillis = Math.max(1000L / ratePerSecond, 1L);
        nextAllowedSendAt = clock.instant().plusMillis(spacingMillis);
    }

    private void sleepUntil(Instant target, Instant now) {
        long millis = Duration.between(now, target).toMillis();
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
