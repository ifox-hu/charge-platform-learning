package com.chargeplatform.order.event;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@ConditionalOnProperty(name = "app.rabbit.enabled", havingValue = "true")
public class RabbitOrderEventBridge {
    private static final Logger log = LoggerFactory.getLogger(RabbitOrderEventBridge.class);
    private final RabbitTemplate rabbitTemplate;
    private final Environment environment;

    public RabbitOrderEventBridge(RabbitTemplate rabbitTemplate, Environment environment) {
        this.rabbitTemplate = rabbitTemplate;
        this.environment = environment;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(OrderCompletedEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    environment.getProperty("app.rabbit.exchange", "charge.order.exchange"),
                    environment.getProperty("app.rabbit.routing-key", "order.completed"),
                    event);
        } catch (Exception exception) {
            // The order is already committed. Keep the API successful and leave the failure visible in logs.
            log.error("订单已落库，但完成事件发布失败: orderId={}", event.orderId(), exception);
        }
    }
}
