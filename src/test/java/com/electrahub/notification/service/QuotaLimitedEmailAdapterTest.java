package com.electrahub.notification.service;

import com.electrahub.notification.domain.Channel;
import com.electrahub.notification.domain.NotificationMessage;
import com.electrahub.notification.repository.NotificationMessageRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuotaLimitedEmailAdapterTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-17T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void disabledEmailFailsWithoutCallingProvider() {
        NotificationMessageRepository repository = mock(NotificationMessageRepository.class);
        ChannelAdapter delegate = mock(ChannelAdapter.class);
        QuotaLimitedEmailAdapter adapter = new QuotaLimitedEmailAdapter(repository, delegate, CLOCK, false, 59, 1);

        DispatchResult result = adapter.dispatch(message());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo("EMAIL_DISABLED");
        verify(delegate, never()).dispatch(any());
    }

    @Test
    void exhaustedDailyQuotaFailsWithoutCallingProvider() {
        NotificationMessageRepository repository = mock(NotificationMessageRepository.class);
        ChannelAdapter delegate = mock(ChannelAdapter.class);
        when(repository.countAttemptedSince(eq(Channel.EMAIL), any())).thenReturn(59L);
        QuotaLimitedEmailAdapter adapter = new QuotaLimitedEmailAdapter(repository, delegate, CLOCK, true, 59, 1);

        DispatchResult result = adapter.dispatch(message());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("EMAIL_DAILY_QUOTA_EXCEEDED");
        verify(delegate, never()).dispatch(any());
    }

    private NotificationMessage message() {
        return new NotificationMessage("tenant-1", "event-1", "key-1", "user-1", Channel.EMAIL, "template");
    }
}
