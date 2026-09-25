package com.chargeplatform.common.api;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class DependencyHealthController {
    private final boolean redisEnabled;
    private final boolean rabbitEnabled;
    private final ObjectProvider<RedisConnectionFactory> redisFactories;
    private final ObjectProvider<ConnectionFactory> rabbitFactories;

    public DependencyHealthController(
            @Value("${app.redis.enabled:false}") boolean redisEnabled,
            @Value("${app.rabbit.enabled:false}") boolean rabbitEnabled,
            ObjectProvider<RedisConnectionFactory> redisFactories,
            ObjectProvider<ConnectionFactory> rabbitFactories) {
        this.redisEnabled = redisEnabled;
        this.rabbitEnabled = rabbitEnabled;
        this.redisFactories = redisFactories;
        this.rabbitFactories = rabbitFactories;
    }

    @GetMapping("/dependencies")
    public ApiResponse<Map<String, String>> dependencies() {
        Map<String, String> result = new LinkedHashMap<>();
        if (!redisEnabled) {
            result.put("redis", "DISABLED");
        } else {
            result.put("redis", pingRedis());
        }
        result.put("rabbitmq", rabbitEnabled ? pingRabbit() : "DISABLED");
        return ApiResponse.success(result);
    }

    private String pingRedis() {
        RedisConnectionFactory factory = redisFactories.getIfAvailable();
        if (factory == null) return "UNAVAILABLE";
        try (var connection = factory.getConnection()) {
            return "PONG".equalsIgnoreCase(connection.ping()) ? "UP" : "UNHEALTHY";
        } catch (Exception exception) {
            return "UNAVAILABLE";
        }
    }

    private String pingRabbit() {
        ConnectionFactory factory = rabbitFactories.getIfAvailable();
        if (factory == null) return "UNAVAILABLE";
        try (var connection = factory.createConnection()) {
            return connection.isOpen() ? "UP" : "UNHEALTHY";
        } catch (Exception exception) {
            return "UNAVAILABLE";
        }
    }
}
