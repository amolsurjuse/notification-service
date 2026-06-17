package com.electrahub.notification.config;

import com.electrahub.notification.domain.Channel;
import com.electrahub.notification.repository.NotificationMessageRepository;
import com.electrahub.notification.service.ChannelAdapter;
import com.electrahub.notification.service.NoopChannelAdapter;
import com.electrahub.notification.service.QuotaLimitedEmailAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.List;

@Configuration
public class ChannelAdapterConfig {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    List<ChannelAdapter> channelAdapters(
            NotificationMessageRepository notificationRepository,
            Clock clock,
            @Value("${notification.channel.email.real-send-enabled:false}") boolean emailEnabled,
            @Value("${notification.channel.email.daily-limit:59}") int emailDailyLimit,
            @Value("${notification.channel.email.rate-per-second:1}") int emailRatePerSecond
    ) {
        return List.of(
                new QuotaLimitedEmailAdapter(
                        notificationRepository,
                        new NoopChannelAdapter(Channel.EMAIL),
                        clock,
                        emailEnabled,
                        emailDailyLimit,
                        emailRatePerSecond
                ),
                new NoopChannelAdapter(Channel.SMS),
                new NoopChannelAdapter(Channel.PUSH),
                new NoopChannelAdapter(Channel.IN_APP)
        );
    }
}
