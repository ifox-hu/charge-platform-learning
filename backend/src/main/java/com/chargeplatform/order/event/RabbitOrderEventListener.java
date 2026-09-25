package com.chargeplatform.order.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.rabbit.enabled", havingValue = "true")
public class RabbitOrderEventListener {
    private static final Logger log = LoggerFactory.getLogger(RabbitOrderEventListener.class);

    @RabbitListener(queues = "${app.rabbit.queue:charge.order.completed}")
    public void handle(OrderCompletedEvent event) {
        log.info("订单完成事件已异步消费: orderId={}, stationId={}, totalAmount={}",
                event.orderId(), event.stationId(), event.totalAmount());
    }
}
