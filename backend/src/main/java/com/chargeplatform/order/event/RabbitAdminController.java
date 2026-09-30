package com.chargeplatform.order.event;

import com.chargeplatform.common.dto.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/rabbitmq")
@PreAuthorize("hasRole('ADMIN')")
@ConditionalOnProperty(name = "app.rabbit.enabled", havingValue = "true")
public class RabbitAdminController {
    private final RabbitAdminService service;
    public RabbitAdminController(RabbitAdminService service) { this.service = service; }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() { return ApiResponse.success(service.overview()); }

    @PostMapping("/dead-letters/retry")
    public ApiResponse<Map<String, Object>> retry(@RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.success(Map.of("count", service.retryDeadLetters(limit)));
    }

    @DeleteMapping("/dead-letters")
    public ApiResponse<Map<String, Object>> purge() {
        return ApiResponse.success(Map.of("count", service.purgeDeadLetters()));
    }
}
