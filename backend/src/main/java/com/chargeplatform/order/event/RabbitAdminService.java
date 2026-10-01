package com.chargeplatform.order.event;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "app.rabbit.enabled", havingValue = "true")
public class RabbitAdminService {
    private static final Logger log = LoggerFactory.getLogger(RabbitAdminService.class);
    private final RabbitAdmin admin;
    private final RabbitTemplate template;
    private final Environment env;

    public RabbitAdminService(RabbitAdmin admin, RabbitTemplate template, Environment env) {
        this.admin = admin; this.template = template; this.env = env;
    }

    public Map<String, Object> overview() {
        String queue = queue(); String dlq = deadQueue();
        return queueInfo(queue, false, dlq);
    }

    public int retryDeadLetters(int limit) {
        int max = Math.max(1, Math.min(limit, 500));
        int available = count(deadQueue(), "QUEUE_MESSAGE_COUNT");
        if (available == 0) return 0;
        int count = 0;
        try {
            while (count < Math.min(max, available)) {
                Message message = template.receive(deadQueue(), 100);
                if (message == null) break;
                template.send(exchange(), routingKey(), message);
                count++;
            }
            return count;
        } catch (Exception exception) {
            log.error("重试 RabbitMQ 死信失败: queue={}, exchange={}, routingKey={}, retried={}",
                    deadQueue(), exchange(), routingKey(), count, exception);
            throw new IllegalStateException("RabbitMQ 死信队列暂时不可用，请检查 RabbitMQ 连接和队列配置", exception);
        }
    }

    public int purgeDeadLetters() {
        int count = count(deadQueue(), "QUEUE_MESSAGE_COUNT");
        admin.purgeQueue(deadQueue(), false);
        return count;
    }

    private Map<String, Object> queueInfo(String queue, boolean ignored, String dlq) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exchange", exchange()); result.put("routingKey", routingKey());
        result.put("queue", queue); result.put("deadLetterQueue", dlq);
        result.put("ready", count(queue, "QUEUE_MESSAGE_COUNT"));
        result.put("consumers", count(queue, "QUEUE_CONSUMER_COUNT"));
        result.put("deadLetters", count(dlq, "QUEUE_MESSAGE_COUNT"));
        result.put("deadLetterConsumers", count(dlq, "QUEUE_CONSUMER_COUNT"));
        return result;
    }

    private int count(String queue, String key) {
        try { Object value = admin.getQueueProperties(queue).get(key); return value instanceof Number n ? n.intValue() : 0; }
        catch (Exception e) { return 0; }
    }
    private String exchange() { return env.getProperty("app.rabbit.exchange", "charge.order.exchange"); }
    private String routingKey() { return env.getProperty("app.rabbit.routing-key", "order.completed"); }
    private String queue() { return env.getProperty("app.rabbit.queue", "charge.order.completed"); }
    private String deadQueue() { return env.getProperty("app.rabbit.dead-letter-queue", "charge.order.completed.dlq"); }
}
