package com.electrahub.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
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
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        factory.setDefaultRequeueRejected(false);
        factory.setConcurrentConsumers(2);
        factory.setMaxConcurrentConsumers(8);
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
            DirectExchange notificationExchange,
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
            DirectExchange notificationExchange,
            Queue notificationDomainEventQueue,
            @Value("${notification.broker.domain-event-routing-key}") String routingKey
    ) {
        return BindingBuilder.bind(notificationDomainEventQueue).to(notificationExchange).with(routingKey);
    }
}
