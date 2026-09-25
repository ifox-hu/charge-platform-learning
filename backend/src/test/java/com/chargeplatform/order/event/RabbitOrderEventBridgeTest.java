package com.chargeplatform.order.event;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.env.Environment;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RabbitOrderEventBridgeTest {

    @Test
    void brokerFailureDoesNotTurnCommittedOrderIntoApiFailure() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        Environment environment = mock(Environment.class);
        when(environment.getProperty("app.rabbit.exchange", "charge.order.exchange"))
                .thenReturn("charge.order.exchange");
        when(environment.getProperty("app.rabbit.routing-key", "order.completed"))
                .thenReturn("order.completed");
        doThrow(new IllegalStateException("broker unavailable"))
                .when(rabbitTemplate).convertAndSend(eq("charge.order.exchange"), eq("order.completed"), any(OrderCompletedEvent.class));

        RabbitOrderEventBridge bridge = new RabbitOrderEventBridge(rabbitTemplate, environment);
        OrderCompletedEvent event = new OrderCompletedEvent(1L, 2L, new BigDecimal("12.00"));

        assertDoesNotThrow(() -> bridge.publish(event));
        verify(rabbitTemplate).convertAndSend("charge.order.exchange", "order.completed", event);
    }
}
