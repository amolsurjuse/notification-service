package com.electrahub.notification.config;

import com.electrahub.notification.domain.Channel;
import com.electrahub.notification.repository.NotificationMessageRepository;
import com.electrahub.notification.repository.PushDeviceRegistrationRepository;
import com.electrahub.notification.service.ChannelAdapter;
import com.electrahub.notification.service.FirebasePushAdapter;
import com.electrahub.notification.service.FirebasePushSender;
import com.electrahub.notification.service.NoopChannelAdapter;
import com.electrahub.notification.service.QuotaLimitedEmailAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
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
            PushDeviceRegistrationRepository pushDeviceRepository,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${notification.channel.email.real-send-enabled:false}") boolean emailEnabled,
            @Value("${notification.channel.email.daily-limit:59}") int emailDailyLimit,
            @Value("${notification.channel.email.rate-per-second:1}") int emailRatePerSecond,
            @Value("${notification.channel.push.real-send-enabled:false}") boolean pushEnabled,
            @Value("${notification.channel.push.daily-limit:500}") int pushDailyLimit,
            @Value("${notification.channel.push.rate-per-second:5}") int pushRatePerSecond,
            @Value("${notification.channel.push.firebase.project-id:}") String firebaseProjectId,
            @Value("${notification.channel.push.firebase.service-account-path:}") String firebaseServiceAccountPath,
            @Value("${notification.channel.push.firebase.service-account-json:}") String firebaseServiceAccountJson,
            @Value("${notification.channel.push.firebase.service-account-base64:}") String firebaseServiceAccountBase64
    ) {
        FirebasePushSender firebasePushSender = FirebasePushSender.fromCredentials(
                firebaseProjectId,
                firebaseServiceAccountPath,
                firebaseServiceAccountJson,
                firebaseServiceAccountBase64
        );
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
                new FirebasePushAdapter(
                        notificationRepository,
                        pushDeviceRepository,
                        objectMapper,
                        firebasePushSender,
                        clock,
                        pushEnabled,
                        pushDailyLimit,
                        pushRatePerSecond
                ),
                new NoopChannelAdapter(Channel.IN_APP)
        );
    }
}
