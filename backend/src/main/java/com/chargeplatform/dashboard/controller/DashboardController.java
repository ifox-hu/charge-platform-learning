package com.chargeplatform.dashboard.controller;

import com.chargeplatform.common.dto.ApiResponse;
import com.chargeplatform.dashboard.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<java.util.Map<String, Long>> summary() {
        return ApiResponse.success(service.summary());
    }
}
