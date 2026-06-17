package com.electrahub.notification.service;

import com.electrahub.notification.domain.Channel;
import com.electrahub.notification.domain.NotificationMessage;

public interface ChannelAdapter {
    Channel channel();
    DispatchResult dispatch(NotificationMessage message);
}
