package com.electrahub.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitNotificationConfig {
    @Bean
    JacksonJsonMessageConverter jacksonJsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, JacksonJsonMessageConverter converter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(converter);
        return rabbitTemplate;
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory, JacksonJsonMessageConverter converter) {
        return listenerFactory(connectionFactory, converter);
    }

    @Bean
    SimpleRabbitListenerContainerFactory notificationDispatchRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            JacksonJsonMessageConverter converter,
            RabbitTemplate rabbitTemplate,
            @Value("${notification.broker.dispatch-dead-letter-exchange}") String deadLetterExchange,
            @Value("${notification.broker.dispatch-dead-letter-routing-key}") String deadLetterRoutingKey,
            @Value("${notification.broker.dispatch-retry-max-attempts}") int maxAttempts,
            @Value("${notification.broker.dispatch-retry-initial-interval-ms}") long initialInterval,
            @Value("${notification.broker.dispatch-retry-multiplier}") double multiplier,
            @Value("${notification.broker.dispatch-retry-max-interval-ms}") long maxInterval
    ) {
        SimpleRabbitListenerContainerFactory factory = listenerFactory(connectionFactory, converter);
        RepublishMessageRecoverer recoverer = new RepublishMessageRecoverer(
                rabbitTemplate,
                deadLetterExchange,
                deadLetterRoutingKey
        );
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxRetries(Math.max(0, maxAttempts - 1))
                .backOffOptions(
                        Math.max(1L, initialInterval),
                        Math.max(1.0d, multiplier),
                        Math.max(initialInterval, maxInterval)
                )
                .recoverer(recoverer)
                .build());
        return factory;
    }

    @Bean
    DirectExchange notificationExchange(@Value("${notification.broker.exchange}") String exchange) {
        return new DirectExchange(exchange, true, false);
    }

    @Bean
    Queue notificationDispatchQueue(@Value("${notification.broker.dispatch-queue}") String queue) {
        return new Queue(queue, true);
    }

    @Bean
    Binding notificationDispatchBinding(
            @Qualifier("notificationExchange") DirectExchange notificationExchange,
            Queue notificationDispatchQueue,
            @Value("${notification.broker.dispatch-routing-key}") String routingKey
    ) {
        return BindingBuilder.bind(notificationDispatchQueue).to(notificationExchange).with(routingKey);
    }

    @Bean
    Queue notificationDomainEventQueue(@Value("${notification.broker.domain-event-queue}") String queue) {
        return new Queue(queue, true);
    }

    @Bean
    Binding notificationDomainEventBinding(
            @Qualifier("notificationExchange") DirectExchange notificationExchange,
            Queue notificationDomainEventQueue,
            @Value("${notification.broker.domain-event-routing-key}") String routingKey
    ) {
        return BindingBuilder.bind(notificationDomainEventQueue).to(notificationExchange).with(routingKey);
    }

    @Bean
    FanoutExchange notificationRealtimeExchange(
            @Value("${notification.broker.realtime-exchange}") String exchange
    ) {
        return new FanoutExchange(exchange, true, false);
    }

    @Bean
    AnonymousQueue notificationRealtimeQueue() {
        return new AnonymousQueue();
    }

    @Bean
    Binding notificationRealtimeBinding(
            @Qualifier("notificationRealtimeExchange") FanoutExchange notificationRealtimeExchange,
            @Qualifier("notificationRealtimeQueue") AnonymousQueue notificationRealtimeQueue
    ) {
        return BindingBuilder.bind(notificationRealtimeQueue).to(notificationRealtimeExchange);
    }

    @Bean
    DirectExchange notificationDeadLetterExchange(
            @Value("${notification.broker.dispatch-dead-letter-exchange}") String exchange
    ) {
        return new DirectExchange(exchange, true, false);
    }

    @Bean
    Queue notificationDispatchDeadLetterQueue(
            @Value("${notification.broker.dispatch-dead-letter-queue}") String queue
    ) {
        return new Queue(queue, true);
    }

    @Bean
    Binding notificationDispatchDeadLetterBinding(
            @Qualifier("notificationDeadLetterExchange") DirectExchange notificationDeadLetterExchange,
            Queue notificationDispatchDeadLetterQueue,
            @Value("${notification.broker.dispatch-dead-letter-routing-key}") String routingKey
    ) {
        return BindingBuilder.bind(notificationDispatchDeadLetterQueue)
                .to(notificationDeadLetterExchange)
                .with(routingKey);
    }

    private SimpleRabbitListenerContainerFactory listenerFactory(
            ConnectionFactory connectionFactory,
            JacksonJsonMessageConverter converter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        factory.setDefaultRequeueRejected(false);
        factory.setConcurrentConsumers(2);
        factory.setMaxConcurrentConsumers(8);
        return factory;
    }
}
