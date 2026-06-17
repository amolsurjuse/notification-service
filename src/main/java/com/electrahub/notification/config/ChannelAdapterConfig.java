package com.electrahub.notification.config;

import com.electrahub.notification.domain.Channel;
import com.electrahub.notification.service.ChannelAdapter;
import com.electrahub.notification.service.NoopChannelAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class ChannelAdapterConfig {
    @Bean
    List<ChannelAdapter> channelAdapters() {
        return List.of(
                new NoopChannelAdapter(Channel.EMAIL),
                new NoopChannelAdapter(Channel.SMS),
                new NoopChannelAdapter(Channel.PUSH),
                new NoopChannelAdapter(Channel.IN_APP)
        );
    }
}
