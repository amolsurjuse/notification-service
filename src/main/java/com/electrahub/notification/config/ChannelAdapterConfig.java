package com.electrahub.notification.config;

import com.electrahub.notification.domain.Channel;
import com.electrahub.notification.repository.NotificationMessageRepository;
import com.electrahub.notification.repository.PushDeviceRegistrationRepository;
import com.electrahub.notification.service.ChannelAdapter;
import com.electrahub.notification.service.EmailCompositionService;
import com.electrahub.notification.service.FirebasePushAdapter;
import com.electrahub.notification.service.FirebasePushSender;
import com.electrahub.notification.service.NoopChannelAdapter;
import com.electrahub.notification.service.QuotaLimitedEmailAdapter;
import com.electrahub.notification.service.SmtpEmailAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.time.Clock;
import java.util.List;
import java.util.Properties;

@Configuration
public class ChannelAdapterConfig {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    FirebasePushSender firebasePushSender(
            @Value("${notification.channel.push.firebase.project-id:}") String projectId,
            @Value("${notification.channel.push.firebase.service-account-path:}") String serviceAccountPath,
            @Value("${notification.channel.push.firebase.service-account-json:}") String serviceAccountJson,
            @Value("${notification.channel.push.firebase.service-account-base64:}") String serviceAccountBase64
    ) {
        return FirebasePushSender.fromCredentials(
                projectId,
                serviceAccountPath,
                serviceAccountJson,
                serviceAccountBase64
        );
    }

    @Bean
    ApplicationRunner firebaseCredentialVerifier(
            FirebasePushSender firebasePushSender,
            @Value("${notification.channel.push.real-send-enabled:false}") boolean pushEnabled
    ) {
        return arguments -> {
            if (!pushEnabled) {
                return;
            }
            if (!firebasePushSender.configured()) {
                throw new IllegalStateException("Firebase push delivery is enabled but credentials are not configured");
            }
            firebasePushSender.verifyCredentials();
        };
    }

    @Bean("firebasePush")
    HealthIndicator firebasePushHealthIndicator(
            FirebasePushSender firebasePushSender,
            @Value("${notification.channel.push.real-send-enabled:false}") boolean pushEnabled
    ) {
        return () -> {
            if (!pushEnabled) {
                return Health.up().withDetail("enabled", false).build();
            }
            if (!firebasePushSender.configured()) {
                return Health.down().withDetail("reason", "credentials-not-configured").build();
            }
            return Health.up()
                    .withDetail("enabled", true)
                    .withDetail("projectId", firebasePushSender.projectId())
                    .build();
        };
    }

    @Bean
    List<ChannelAdapter> channelAdapters(
            NotificationMessageRepository notificationRepository,
            PushDeviceRegistrationRepository pushDeviceRepository,
            ObjectMapper objectMapper,
            EmailCompositionService emailCompositionService,
            Clock clock,
            FirebasePushSender firebasePushSender,
            @Value("${notification.channel.email.real-send-enabled:false}") boolean emailEnabled,
            @Value("${notification.channel.email.daily-limit:59}") int emailDailyLimit,
            @Value("${notification.channel.email.rate-per-second:1}") int emailRatePerSecond,
            @Value("${notification.channel.email.provider:smtp-email}") String emailProvider,
            @Value("${notification.channel.email.smtp.host:}") String emailSmtpHost,
            @Value("${notification.channel.email.smtp.port:587}") int emailSmtpPort,
            @Value("${notification.channel.email.smtp.username:}") String emailSmtpUsername,
            @Value("${notification.channel.email.smtp.password:}") String emailSmtpPassword,
            @Value("${notification.channel.email.smtp.from-email:no-reply@notify.electrahub.net}") String emailFromEmail,
            @Value("${notification.channel.email.smtp.from-name:ElectraHub}") String emailFromName,
            @Value("${notification.channel.email.smtp.starttls-enabled:true}") boolean emailStartTlsEnabled,
            @Value("${notification.channel.email.smtp.auth-enabled:true}") boolean emailAuthEnabled,
            @Value("${notification.channel.email.smtp.connection-timeout-ms:10000}") int emailConnectionTimeoutMs,
            @Value("${notification.channel.email.smtp.timeout-ms:10000}") int emailTimeoutMs,
            @Value("${notification.channel.email.smtp.write-timeout-ms:10000}") int emailWriteTimeoutMs,
            @Value("${notification.channel.push.real-send-enabled:false}") boolean pushEnabled,
            @Value("${notification.channel.push.daily-limit:500}") int pushDailyLimit,
            @Value("${notification.channel.push.rate-per-second:5}") int pushRatePerSecond
    ) {
        return List.of(
                new QuotaLimitedEmailAdapter(
                        notificationRepository,
                        new SmtpEmailAdapter(
                                smtpMailSender(
                                        emailSmtpHost,
                                        emailSmtpPort,
                                        emailSmtpUsername,
                                        emailSmtpPassword,
                                        emailStartTlsEnabled,
                                        emailAuthEnabled,
                                        emailConnectionTimeoutMs,
                                        emailTimeoutMs,
                                        emailWriteTimeoutMs
                                ),
                                objectMapper,
                                emailCompositionService,
                                emailProvider,
                                emailSmtpHost,
                                emailFromEmail,
                                emailFromName
                        ),
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

    private JavaMailSender smtpMailSender(
            String host,
            int port,
            String username,
            String password,
            boolean startTlsEnabled,
            boolean authEnabled,
            int connectionTimeoutMs,
            int timeoutMs,
            int writeTimeoutMs
    ) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host == null ? "" : host.trim());
        sender.setPort(port);
        if (username != null && !username.isBlank()) {
            sender.setUsername(username.trim());
        }
        if (password != null && !password.isBlank()) {
            sender.setPassword(password);
        }
        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.transport.protocol", "smtp");
        properties.put("mail.smtp.auth", Boolean.toString(authEnabled));
        properties.put("mail.smtp.starttls.enable", Boolean.toString(startTlsEnabled));
        properties.put("mail.smtp.connectiontimeout", Integer.toString(connectionTimeoutMs));
        properties.put("mail.smtp.timeout", Integer.toString(timeoutMs));
        properties.put("mail.smtp.writetimeout", Integer.toString(writeTimeoutMs));
        return sender;
    }
}
