package com.chargeplatform.audit.controller;

import com.chargeplatform.audit.domain.AuditLog;
import com.chargeplatform.audit.service.AuditLogService;
import com.chargeplatform.common.dto.ApiResponse;
import com.chargeplatform.common.dto.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {
    private final AuditLogService service;

    public AuditLogController(AuditLogService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResponse<AuditLog>> page(@RequestParam(defaultValue = "") String username,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(PageResponse.from(service.page(username, page, size)));
    }

    @DeleteMapping("/before")
    public ApiResponse<Void> cleanup(@RequestParam int days) {
        return ApiResponse.message("已清理 " + service.cleanup(days) + " 条 " + days + " 天前的审计日志");
    }
}
