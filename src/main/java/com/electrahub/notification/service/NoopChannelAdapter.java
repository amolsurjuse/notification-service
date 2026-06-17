package com.electrahub.notification.service;

import com.electrahub.notification.domain.Channel;
import com.electrahub.notification.domain.NotificationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class NoopChannelAdapter implements ChannelAdapter {
    private static final Logger log = LoggerFactory.getLogger(NoopChannelAdapter.class);

    private final Channel channel;

    public NoopChannelAdapter(Channel channel) {
        this.channel = channel;
    }

    @Override
    public Channel channel() {
        return channel;
    }

    @Override
    public DispatchResult dispatch(NotificationMessage message) {
        log.info("No-op dispatch for notification {} channel {} recipient {}", message.getId(), channel, message.getRecipientRef());
        return DispatchResult.success("noop-" + channel.name().toLowerCase(), UUID.randomUUID().toString());
    }
}
