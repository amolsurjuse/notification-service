package com.electrahub.notification.service;

import com.electrahub.notification.domain.Channel;
import com.electrahub.notification.domain.NotificationMessage;
import com.electrahub.notification.repository.NotificationMessageRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationDispatchListenerTest {
    @Test
    void retryableAdapterFailureRecordsAttemptAndSignalsRabbitRetry() {
        NotificationMessageRepository repository = mock(NotificationMessageRepository.class);
        NotificationDispatchStateService stateService = mock(NotificationDispatchStateService.class);
        ChannelAdapter adapter = mock(ChannelAdapter.class);
        NotificationMessage message = new NotificationMessage(
                "electrahub",
                "event-1",
                "key-1",
                "device-1",
                Channel.PUSH,
                "charging-session-started"
        );
        when(repository.findById(message.getId())).thenReturn(Optional.of(message));
        when(adapter.channel()).thenReturn(Channel.PUSH);
        when(adapter.dispatch(message)).thenReturn(DispatchResult.retryableFailure("firebase-fcm", "UNAVAILABLE"));
        NotificationDispatchListener listener = new NotificationDispatchListener(repository, stateService, List.of(adapter));

        assertThatThrownBy(() -> listener.dispatch(new DispatchCommand(message.getId())))
                .isInstanceOf(RetryableNotificationDispatchException.class)
                .hasMessageContaining("UNAVAILABLE");

        verify(stateService).recordRetryableFailure(message.getId(), "UNAVAILABLE");
    }
}
