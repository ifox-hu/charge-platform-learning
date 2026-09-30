package com.chargeplatform.order.event;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration
@EnableRabbit
@ConditionalOnProperty(name = "app.rabbit.enabled", havingValue = "true")
public class RabbitOrderEventConfig {
    @Bean
    RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    TopicExchange orderExchange(org.springframework.core.env.Environment env) {
        return new TopicExchange(env.getProperty("app.rabbit.exchange", "charge.order.exchange"));
    }

    @Bean
    DirectExchange orderDeadLetterExchange(org.springframework.core.env.Environment env) {
        return new DirectExchange(env.getProperty("app.rabbit.dead-letter-exchange", "charge.order.dlx"));
    }

    @Bean
    Queue orderCompletedQueue(org.springframework.core.env.Environment env) {
        return QueueBuilder.durable(env.getProperty("app.rabbit.queue", "charge.order.completed"))
                .deadLetterExchange(env.getProperty("app.rabbit.dead-letter-exchange", "charge.order.dlx"))
                .deadLetterRoutingKey(env.getProperty("app.rabbit.dead-letter-routing-key", "order.completed.failed"))
                .build();
    }

    @Bean
    Queue orderDeadLetterQueue(org.springframework.core.env.Environment env) {
        return QueueBuilder.durable(env.getProperty("app.rabbit.dead-letter-queue", "charge.order.completed.dlq")).build();
    }

    @Bean
    Binding orderDeadLetterBinding(@Qualifier("orderDeadLetterQueue") Queue orderDeadLetterQueue, DirectExchange orderDeadLetterExchange,
                                  org.springframework.core.env.Environment env) {
        return BindingBuilder.bind(orderDeadLetterQueue).to(orderDeadLetterExchange)
                .with(env.getProperty("app.rabbit.dead-letter-routing-key", "order.completed.failed"));
    }

    @Bean
    Binding orderCompletedBinding(@Qualifier("orderCompletedQueue") Queue orderCompletedQueue, TopicExchange orderExchange,
                                  org.springframework.core.env.Environment env) {
        return BindingBuilder.bind(orderCompletedQueue).to(orderExchange)
                .with(env.getProperty("app.rabbit.routing-key", "order.completed"));
    }

    @Bean
    Jackson2JsonMessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean(name = "rabbitListenerContainerFactory")
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, Jackson2JsonMessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(1000L, 2.0, 5000L)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build());
        return factory;
    }
}
