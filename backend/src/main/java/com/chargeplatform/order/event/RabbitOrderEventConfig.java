package com.chargeplatform.order.event;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
@ConditionalOnProperty(name = "app.rabbit.enabled", havingValue = "true")
public class RabbitOrderEventConfig {
    @Bean
    TopicExchange orderExchange(org.springframework.core.env.Environment env) {
        return new TopicExchange(env.getProperty("app.rabbit.exchange", "charge.order.exchange"));
    }

    @Bean
    Queue orderCompletedQueue(org.springframework.core.env.Environment env) {
        return new Queue(env.getProperty("app.rabbit.queue", "charge.order.completed"), true);
    }

    @Bean
    Binding orderCompletedBinding(Queue orderCompletedQueue, TopicExchange orderExchange,
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
